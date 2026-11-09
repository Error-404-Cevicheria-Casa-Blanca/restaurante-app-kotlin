package com.lacasablanca.mesero.ui.mesas

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lacasablanca.mesero.R
import com.lacasablanca.mesero.databinding.BottomSheetAccionesMesaBinding
import com.lacasablanca.mesero.databinding.DialogDividirMesaBinding
import com.lacasablanca.mesero.databinding.DialogSeleccionarMesaBinding
import com.lacasablanca.mesero.domain.model.ItemCarrito
import com.lacasablanca.mesero.domain.model.Mesa
import com.lacasablanca.mesero.ui.common.etiquetaRes

/**
 * UI premium de las operaciones de mesa (PRB-93).
 *
 * Mantiene los dialogos fuera de [com.lacasablanca.mesero.ui.salon.SalonFragment]
 * para no engordar aun mas esa clase. Solo dibuja: cada decision viaja a
 * [Eventos], que reenvia la operacion al ViewModel.
 */
internal class AccionesMesaUi(
    private val fragment: Fragment,
    private val eventos: Eventos
) {

    interface Eventos {
        fun alElegirAccion(accion: AccionMesa, mesa: Mesa)
        fun alConfirmar(
            accion: AccionMesa,
            mesa: Mesa,
            destino: Mesa,
            itemIds: List<Long>
        )
    }

    private var sheet: BottomSheetDialog? = null
    private var dialogoDivision: AlertDialog? = null
    private var dialogoDestino: AlertDialog? = null

    /** Abre el menu de 3 acciones de la mesa. */
    fun abrirMenu(mesa: Mesa) {
        cerrar()

        val binding = BottomSheetAccionesMesaBinding.inflate(fragment.layoutInflater)
        binding.textoAccionesTitulo.text =
            fragment.getString(R.string.acciones_mesa_titulo, mesa.numero)

        val hoja = BottomSheetDialog(fragment.requireContext())
        hoja.setContentView(binding.root)
        binding.btnCerrarAcciones.setOnClickListener { hoja.dismiss() }
        binding.accionDividir.setOnClickListener {
            hoja.dismiss()
            eventos.alElegirAccion(AccionMesa.DIVIDIR, mesa)
        }
        binding.accionFusionar.setOnClickListener {
            hoja.dismiss()
            eventos.alElegirAccion(AccionMesa.FUSIONAR, mesa)
        }
        binding.accionTransferir.setOnClickListener {
            hoja.dismiss()
            eventos.alElegirAccion(AccionMesa.TRANSFERIR, mesa)
        }
        hoja.setOnShowListener { configurarSheet(hoja) }
        hoja.setOnDismissListener { if (sheet === hoja) sheet = null }
        hoja.show()
        sheet = hoja
    }

    /**
     * Paso 1 de la division: elegir que platos pasan a la cuenta nueva.
     * Al continuar se abre el dialogo de mesa destino con los ids elegidos.
     */
    fun mostrarDivision(
        mesa: Mesa,
        lineas: List<ItemCarrito>,
        destinos: List<Mesa>
    ) {
        cerrar()
        if (lineas.isEmpty() || destinos.isEmpty()) return

        val binding = DialogDividirMesaBinding.inflate(fragment.layoutInflater)
        binding.tituloDividir.text = fragment.getString(R.string.division_titulo, mesa.numero)
        binding.mensajeDividir.text = fragment.getString(R.string.division_mensaje)

        var seleccion = lineas.map { DivisionLineaItem(it, seleccionada = false) }
        lateinit var adapter: DivisionLineaAdapter
        adapter = DivisionLineaAdapter { actualizado ->
            seleccion = seleccion.map {
                if (it.itemId == actualizado.itemId) actualizado else it
            }
            adapter.submitList(seleccion)
            binding.errorDividir.isVisible = false
        }
        binding.listaItemsDividir.layoutManager = LinearLayoutManager(fragment.requireContext())
        binding.listaItemsDividir.adapter = adapter
        adapter.submitList(seleccion)

        val alerta = crearDialogo(binding.root)
        binding.btnCancelarDividir.setOnClickListener { alerta.dismiss() }
        binding.btnContinuarDividir.setOnClickListener {
            val ids = adapter.seleccionados()
            if (ids.isEmpty()) {
                binding.errorDividir.isVisible = true
                return@setOnClickListener
            }
            alerta.dismiss()
            mostrarDestinos(AccionMesa.DIVIDIR, mesa, destinos, ids)
        }
        alerta.setOnDismissListener {
            if (dialogoDivision === alerta) dialogoDivision = null
            binding.listaItemsDividir.adapter = null
        }
        alerta.show()
        aplicarAncho(alerta)
        dialogoDivision = alerta
    }

    /**
     * Paso 2 (o unico paso en fusion/transferencia): elegir mesa destino.
     * El boton Confirmar queda deshabilitado hasta que haya seleccion.
     */
    fun mostrarDestinos(
        accion: AccionMesa,
        mesa: Mesa,
        destinos: List<Mesa>,
        itemIds: List<Long> = emptyList()
    ) {
        cerrar()
        if (destinos.isEmpty()) return
        if (accion == AccionMesa.DIVIDIR && itemIds.isEmpty()) return

        val binding = DialogSeleccionarMesaBinding.inflate(fragment.layoutInflater)
        binding.tituloSeleccionMesa.text = tituloDestino(accion, mesa)
        binding.mensajeSeleccionMesa.text = mensajeDestino(accion, mesa)
        binding.btnConfirmarDestino.isEnabled = false

        var seleccionado: Mesa? = null
        var items = destinos.map { destino ->
            DestinoMesaItem(
                mesa = destino,
                etiqueta = etiquetaDestino(accion, mesa, destino),
                seleccionada = false
            )
        }
        lateinit var adapter: DestinoMesaAdapter
        adapter = DestinoMesaAdapter { elegido ->
            seleccionado = elegido.mesa
            items = items.map { it.copy(seleccionada = it.mesa.id == elegido.mesa.id) }
            adapter.submitList(items)
            binding.btnConfirmarDestino.isEnabled = true
        }
        binding.listaDestinos.layoutManager = LinearLayoutManager(fragment.requireContext())
        binding.listaDestinos.adapter = adapter
        adapter.submitList(items)

        val alerta = crearDialogo(binding.root)
        binding.btnCancelarDestino.setOnClickListener { alerta.dismiss() }
        binding.btnConfirmarDestino.setOnClickListener {
            val destino = seleccionado ?: return@setOnClickListener
            ponerEnProgreso(alerta, binding)
            eventos.alConfirmar(accion, mesa, destino, itemIds)
        }
        alerta.setOnDismissListener {
            if (dialogoDestino === alerta) dialogoDestino = null
            binding.listaDestinos.adapter = null
        }
        alerta.show()
        aplicarAncho(alerta)
        dialogoDestino = alerta
    }

    /** Cierra todo lo que esta abierto. Se llama al recibir cualquier resultado. */
    fun cerrar() {
        sheet?.dismiss()
        sheet = null
        dialogoDivision?.dismiss()
        dialogoDivision = null
        dialogoDestino?.dismiss()
        dialogoDestino = null
    }

    private fun configurarSheet(hoja: BottomSheetDialog) {
        val anchoMaximo = fragment.resources.getDimensionPixelSize(
            R.dimen.ancho_maximo_sheet_acciones
        )
        val ancho = if (fragment.resources.configuration.screenWidthDp >= ANCHO_TABLET_DP) {
            minOf(fragment.resources.displayMetrics.widthPixels, anchoMaximo)
        } else {
            ViewGroup.LayoutParams.MATCH_PARENT
        }
        hoja.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setDimAmount(DIM_AMOUNT)
            setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
            setLayout(ancho, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        hoja.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            ?.setBackgroundColor(Color.TRANSPARENT)
        hoja.behavior.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
            isDraggable = true
        }
    }

    private fun crearDialogo(contenido: View): AlertDialog =
        MaterialAlertDialogBuilder(
            fragment.requireContext(),
            R.style.ThemeOverlay_LaCasaBlanca_Dialogo_LiberarMesa
        )
            .setView(contenido)
            .setCancelable(true)
            .create()

    private fun aplicarAncho(alerta: AlertDialog) {
        val anchoPantalla = (fragment.resources.displayMetrics.widthPixels * FRACCION_ANCHO).toInt()
        val anchoMaximo = fragment.resources.getDimensionPixelSize(
            R.dimen.ancho_maximo_dialogo_acciones
        )
        alerta.window?.setLayout(
            minOf(anchoPantalla, anchoMaximo),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun ponerEnProgreso(
        alerta: AlertDialog,
        binding: DialogSeleccionarMesaBinding
    ) {
        binding.listaDestinos.isVisible = false
        binding.grupoBotones.isVisible = false
        binding.grupoProgreso.isVisible = true
        alerta.setCancelable(false)
        alerta.setCanceledOnTouchOutside(false)
    }

    private fun tituloDestino(accion: AccionMesa, mesa: Mesa): String = when (accion) {
        AccionMesa.DIVIDIR -> fragment.getString(R.string.destino_titulo_dividir)
        AccionMesa.FUSIONAR -> fragment.getString(R.string.destino_titulo_fusionar, mesa.numero)
        AccionMesa.TRANSFERIR -> fragment.getString(R.string.destino_titulo_transferir, mesa.numero)
    }

    private fun mensajeDestino(accion: AccionMesa, mesa: Mesa): String = when (accion) {
        AccionMesa.DIVIDIR -> fragment.getString(R.string.destino_mensaje_dividir)
        AccionMesa.FUSIONAR -> fragment.getString(R.string.destino_mensaje_fusionar, mesa.numero)
        AccionMesa.TRANSFERIR -> fragment.getString(R.string.destino_mensaje_transferir, mesa.numero)
    }

    private fun etiquetaDestino(accion: AccionMesa, mesa: Mesa, destino: Mesa): String =
        if (accion == AccionMesa.DIVIDIR && destino.id == mesa.id) {
            fragment.getString(R.string.destino_misma_mesa)
        } else {
            fragment.getString(destino.estado.etiquetaRes())
        }

    private companion object {
        const val ANCHO_TABLET_DP = 600
        const val FRACCION_ANCHO = 0.92f
        const val DIM_AMOUNT = 0.5f
    }
}
