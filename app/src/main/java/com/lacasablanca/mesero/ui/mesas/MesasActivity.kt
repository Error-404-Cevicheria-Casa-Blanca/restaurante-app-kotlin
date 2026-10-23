package com.lacasablanca.mesero.ui.mesas

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.lacasablanca.mesero.MeseroApp
import com.lacasablanca.mesero.R
import com.lacasablanca.mesero.core.GenericViewModelFactory
import com.lacasablanca.mesero.databinding.ActivityMesasBinding
import com.lacasablanca.mesero.databinding.ItemLeyendaBinding
import com.lacasablanca.mesero.domain.model.EstadoMesa
import com.lacasablanca.mesero.domain.model.Mesa
import com.lacasablanca.mesero.ui.comanda.ComandaActivity
import com.lacasablanca.mesero.ui.common.colorRes
import com.lacasablanca.mesero.ui.common.etiquetaPluralRes
import com.lacasablanca.mesero.ui.common.etiquetaRes
import com.lacasablanca.mesero.ui.common.iconoRes
import com.lacasablanca.mesero.ui.login.LoginActivity
import kotlinx.coroutines.launch

/**
 * Mapa de mesas: toolbar, pestanas de zona, chips de estado con contadores,
 * cuadricula (2 columnas en telefono, 4 en tablet) y estados cargando/vacio/error.
 */
class MesasActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMesasBinding
    private lateinit var adapter: MesaAdapter

    private val contenedor get() = (application as MeseroApp).container

    private val viewModel: MesasViewModel by viewModels {
        GenericViewModelFactory {
            MesasViewModel(contenedor.mesaRepository, contenedor.authRepository)
        }
    }

    private var actualizandoTabs = false
    private var firmaTabs = ""
    private var firmaChips = ""
    private var primeraReanudacion = true
    private var dialogoAbierto: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMesasBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = MesaAdapter { mesa -> viewModel.seleccionarMesa(mesa) }
        binding.recyclerMesas.layoutManager =
            GridLayoutManager(this, resources.getInteger(R.integer.columnas_mesas))
        binding.recyclerMesas.adapter = adapter

        configurarPestanas()
        pintarLeyenda()

        binding.barra.btnActualizar.setOnClickListener { viewModel.refrescar() }
        binding.barra.btnCerrarSesion.setOnClickListener { viewModel.cerrarSesion() }
        binding.btnReintentar.setOnClickListener { viewModel.cargar() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.estado.collect(::render) }
                launch { viewModel.eventos.collect(::manejarEvento) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (primeraReanudacion) {
            primeraReanudacion = false
        } else {
            // Al volver de la comanda (o de segundo plano) el mapa se sincroniza
            // con el repositorio: la mesa abierta debe verse en azul (ATENDIDA).
            viewModel.recargarAlVolver()
        }
    }

    override fun onDestroy() {
        dialogoAbierto?.dismiss()
        dialogoAbierto = null
        super.onDestroy()
    }

    private fun configurarPestanas() {
        binding.tabsZonas.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                if (!actualizandoTabs) viewModel.filtrarZona(tab.tag as? String)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) = Unit

            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
    }

    private fun render(estado: MesasUiState) {
        binding.barra.textoSaludo.isVisible = estado.nombreUsuario.isNotBlank()
        binding.barra.textoSaludo.text = getString(R.string.mesas_saludo, estado.nombreUsuario)

        binding.progresoCarga.isVisible = estado.cargando
        binding.barra.btnActualizar.isEnabled = !estado.cargando

        val hayError = estado.error != null && !estado.cargando
        val vacio = estado.sinMesas || estado.sinResultados
        binding.grupoError.isVisible = hayError
        binding.grupoVacio.isVisible = !hayError && vacio
        binding.recyclerMesas.isVisible = !estado.cargando && !hayError && !vacio

        estado.error?.let { binding.textoErrorMesas.text = getString(it.mensajeRes()) }

        renderTabs(estado.zonas, estado.zonaSeleccionada)
        renderChips(estado)
        adapter.submitList(estado.mesasVisibles)
    }

    private fun renderTabs(zonas: List<String>, seleccionada: String?) {
        val firma = zonas.joinToString(separator = "|") + "#" + (seleccionada ?: "")
        if (firma == firmaTabs) return
        firmaTabs = firma

        actualizandoTabs = true
        binding.tabsZonas.removeAllTabs()
        binding.tabsZonas.addTab(
            binding.tabsZonas.newTab().setText(R.string.zona_todas).setTag(null)
        )
        zonas.forEach { zona ->
            binding.tabsZonas.addTab(binding.tabsZonas.newTab().setText(zona).setTag(zona))
        }
        val indice = zonas.indexOf(seleccionada).let { if (it < 0) 0 else it + 1 }
        binding.tabsZonas.getTabAt(indice)?.select()
        actualizandoTabs = false
    }

    private fun renderChips(estado: MesasUiState) {
        val contadores = estado.contadores
        val firma = EstadoMesa.filtrables.joinToString(separator = ",") { "${it.name}=${contadores[it] ?: 0}" } +
            "#" + (estado.estadoSeleccionado?.name ?: "TODAS")
        if (firma == firmaChips) return
        firmaChips = firma

        binding.chipsEstados.removeAllViews()
        val chipTodas = crearChip(
            texto = getString(R.string.zona_todas),
            etiquetaEstado = null,
            icono = null
        )
        binding.chipsEstados.addView(chipTodas)

        val chipsPorEstado = mutableMapOf<EstadoMesa, Chip>()
        EstadoMesa.filtrables.forEach { estadoMesa ->
            val cantidad = contadores[estadoMesa] ?: 0
            val chip = crearChip(
                texto = getString(
                    R.string.chip_estado_con_contador,
                    getString(estadoMesa.etiquetaPluralRes()),
                    cantidad
                ),
                etiquetaEstado = estadoMesa,
                icono = estadoMesa.iconoRes()
            )
            chipsPorEstado[estadoMesa] = chip
            binding.chipsEstados.addView(chip)
        }

        // Se marca despues de agregarlos: el grupo aplica singleSelection al anadir.
        val chipSeleccionado = estado.estadoSeleccionado?.let { chipsPorEstado[it] } ?: chipTodas
        chipSeleccionado.isChecked = true
    }

    private fun crearChip(
        texto: String,
        etiquetaEstado: EstadoMesa?,
        icono: Int?
    ): Chip {
        val chip = Chip(this)
        chip.text = texto
        chip.isCheckable = true
        chip.isClickable = true
        chip.isCheckedIconVisible = false
        chip.isChecked = false
        // Colores exactos de Stitch: activo teal #003D48 / inactivo blanco (pill).
        chip.chipBackgroundColor = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(
                ContextCompat.getColor(this, R.color.chip_filtro_activo),
                ContextCompat.getColor(this, R.color.chip_filtro_inactivo)
            )
        )
        chip.setTextColor(
            ContextCompat.getColorStateList(this, R.color.selector_texto_chip)
        )
        chip.chipIconTint =
            ContextCompat.getColorStateList(this, R.color.selector_icono_chip)
        chip.shapeAppearanceModel = chip.shapeAppearanceModel
            .toBuilder()
            .setAllCornerSizes(resources.getDimension(R.dimen.radio_pill))
            .build()
        chip.chipMinHeight = resources.getDimension(R.dimen.alto_chip_filtro)
        chip.contentDescription = buildString {
            append(texto)
            if (etiquetaEstado != null) {
                append(", ")
                append(getString(etiquetaEstado.etiquetaRes()))
            }
        }
        if (icono != null) {
            chip.chipIcon = ContextCompat.getDrawable(this, icono)
            chip.isChipIconVisible = true
        }
        chip.setOnClickListener { viewModel.filtrarEstado(etiquetaEstado) }
        return chip
    }

    private fun pintarLeyenda() {
        binding.leyendaEstados.removeAllViews()
        EstadoMesa.filtrables.forEach { estado ->
            val item = ItemLeyendaBinding.inflate(layoutInflater, binding.leyendaEstados, false)
            item.puntoEstado.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(this, estado.colorRes()))
            item.textoLeyenda.text = getString(estado.etiquetaRes())
            val margen = resources.getDimensionPixelSize(R.dimen.espacio_l)
            item.root.layoutParams = (item.root.layoutParams as android.widget.LinearLayout.LayoutParams).apply {
                marginEnd = margen
            }
            binding.leyendaEstados.addView(item.root)
        }
    }

    private fun manejarEvento(evento: MesasEvento) {
        when (evento) {
            is MesasEvento.IrAComanda -> abrirComanda(evento.mesa)
            is MesasEvento.Aviso -> mostrarAviso(evento)
            MesasEvento.SesionExpirada -> mostrarDialogoSesionExpirada()
            MesasEvento.SesionCerrada -> irALogin()
        }
    }

    /**
     * Avisos del mapa: los estados de mesa y los errores se explican con un
     * MaterialAlertDialog; la confirmacion de refresco usa un Snackbar de marca.
     */
    private fun mostrarAviso(evento: MesasEvento.Aviso) {
        val mesa = evento.mesa
        when (evento.tipo) {
            AvisoMesas.ACTUALIZADAS -> mostrarSnackbar(getString(R.string.mesas_actualizadas))

            AvisoMesas.MESA_NO_LIBRE -> if (mesa == null) {
                mostrarDialogo(
                    titulo = getString(R.string.mesas_titulo),
                    mensaje = getString(R.string.error_generico)
                )
            } else {
                // Las mesas amarillas y azules tambien responden al toque (D12).
                mostrarDialogo(
                    titulo = getString(R.string.dialogo_mesa_titulo, mesa.numero),
                    mensaje = getString(mesa.mensajeEstadoRes(), mesa.numero)
                )
            }

            AvisoMesas.MESA_TOMADA -> mostrarDialogo(
                titulo = getString(R.string.mesa_tomada_titulo),
                mensaje = mesa?.let { getString(R.string.mesa_tomada, it.numero) }
                    ?: getString(R.string.error_generico)
            )

            AvisoMesas.ERROR_ABRIR -> mostrarDialogo(
                titulo = getString(R.string.error_abrir_mesa_titulo),
                mensaje = getString(R.string.error_abrir_mesa)
            )

            AvisoMesas.ERROR_LIBERAR -> mostrarDialogo(
                titulo = getString(R.string.error_liberar_mesa_titulo),
                mensaje = mesa?.let { getString(R.string.error_liberar_mesa, it.numero) }
                    ?: getString(R.string.error_generico)
            )

            AvisoMesas.ERROR_RED -> mostrarDialogo(
                titulo = getString(R.string.error_red_titulo),
                mensaje = getString(R.string.error_red)
            )
        }
    }

    @StringRes
    private fun Mesa.mensajeEstadoRes(): Int = when (estado) {
        EstadoMesa.OCUPADA -> R.string.mesa_dialogo_ocupada
        EstadoMesa.ATENDIDA -> R.string.mesa_dialogo_atendida
        EstadoMesa.CUENTA_SOLICITADA -> R.string.mesa_dialogo_cuenta
        else -> R.string.mesa_dialogo_no_libre
    }

    private fun mostrarDialogo(
        titulo: CharSequence,
        mensaje: CharSequence,
        cancelable: Boolean = true,
        alAceptar: (() -> Unit)? = null
    ) {
        dialogoAbierto?.dismiss()
        val dialogo = MaterialAlertDialogBuilder(this)
            .setTitle(titulo)
            .setMessage(mensaje)
            .setCancelable(cancelable)
            .setPositiveButton(R.string.accion_aceptar) { dialog, _ ->
                dialog.dismiss()
                alAceptar?.invoke()
            }
            .create()
        dialogo.setCanceledOnTouchOutside(cancelable)
        dialogo.setOnDismissListener { if (dialogoAbierto === dialogo) dialogoAbierto = null }
        dialogo.show()
        dialogoAbierto = dialogo
    }

    private fun mostrarDialogoSesionExpirada() {
        mostrarDialogo(
            titulo = getString(R.string.sesion_expirada_titulo),
            mensaje = getString(R.string.sesion_expirada),
            cancelable = false,
            alAceptar = ::irALogin
        )
    }

    private fun mostrarSnackbar(texto: String) {
        val snackbar = Snackbar.make(binding.root, texto, Snackbar.LENGTH_SHORT)
        snackbar.setTextColor(ContextCompat.getColor(this, R.color.on_primary))
        snackbar.show()
        snackbar.view.background = ContextCompat.getDrawable(this, R.drawable.bg_snackbar)
    }

    private fun abrirComanda(mesa: Mesa) {
        val intent = Intent(this, ComandaActivity::class.java).apply {
            putExtra(ComandaActivity.EXTRA_MESA_ID, mesa.id)
            putExtra(ComandaActivity.EXTRA_MESA_NUMERO, mesa.numero)
            putExtra(ComandaActivity.EXTRA_MESA_UBICACION, mesa.ubicacion)
        }
        startActivity(intent)
    }

    private fun irALogin() {
        val intent = Intent(this, LoginActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(intent)
        finish()
    }

    @StringRes
    private fun ErrorMesas.mensajeRes(): Int = when (this) {
        ErrorMesas.RED -> R.string.error_red
        ErrorMesas.SESION_EXPIRADA -> R.string.sesion_expirada
        ErrorMesas.SERVIDOR -> R.string.mesas_error
        ErrorMesas.DESCONOCIDO -> R.string.mesas_error
    }
}
