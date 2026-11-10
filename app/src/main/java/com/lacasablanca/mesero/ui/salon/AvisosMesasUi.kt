package com.lacasablanca.mesero.ui.salon

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.lacasablanca.mesero.R
import com.lacasablanca.mesero.databinding.DialogAvisoMesaBinding
import com.lacasablanca.mesero.domain.model.EstadoMesa
import com.lacasablanca.mesero.domain.model.Mesa
import com.lacasablanca.mesero.ui.common.iconoRes
import com.lacasablanca.mesero.ui.mesas.AccionMesa
import com.lacasablanca.mesero.ui.mesas.AvisoMesas
import com.lacasablanca.mesero.ui.mesas.MesasEvento
import com.lacasablanca.mesero.ui.mesas.MesasViewModel

/**
 * Avisos del mapa (PRB-93: extraidos de [SalonFragment] para mantener la clase
 * bajo el umbral de detekt). Dibuja dialogos de marca y Snackbars; no contiene
 * logica de negocio.
 */
internal class AvisosMesasUi(
    private val fragment: Fragment,
    private val alAceptarSesionExpirada: () -> Unit
) {

    private var dialogo: AlertDialog? = null

    fun mostrarAviso(evento: MesasEvento.Aviso) {
        val mesa = evento.mesa
        when (evento.tipo) {
            AvisoMesas.ACTUALIZADAS -> mostrarSnackbar(
                fragment.getString(R.string.mesas_actualizadas)
            )

            AvisoMesas.MESA_NO_LIBRE -> mostrarAvisoMesaNoLibre(mesa)

            AvisoMesas.MESA_TOMADA -> mostrarDialogo(
                titulo = fragment.getString(R.string.mesa_tomada_titulo),
                mensaje = mesa?.let { fragment.getString(R.string.mesa_tomada, it.numero) }
                    ?: fragment.getString(R.string.error_generico),
                iconoRes = R.drawable.ic_error_outline
            )

            AvisoMesas.ERROR_ABRIR -> mostrarDialogo(
                titulo = fragment.getString(R.string.error_abrir_mesa_titulo),
                mensaje = fragment.getString(R.string.error_abrir_mesa)
            )

            AvisoMesas.ERROR_LIBERAR -> mostrarDialogo(
                titulo = fragment.getString(R.string.error_liberar_mesa_titulo),
                mensaje = mesa?.let { fragment.getString(R.string.error_liberar_mesa, it.numero) }
                    ?: fragment.getString(R.string.error_generico)
            )

            AvisoMesas.SIN_PLATOS_DIVIDIR,
            AvisoMesas.SIN_DESTINOS,
            AvisoMesas.CONFLICTO_ACCION,
            AvisoMesas.ERROR_ACCION -> mostrarAvisoAccion(evento.tipo)

            AvisoMesas.ERROR_RED -> mostrarDialogo(
                titulo = fragment.getString(R.string.error_red_titulo),
                mensaje = fragment.getString(R.string.error_red)
            )
        }
    }

    fun mostrarExitoAccion(evento: MesasEvento.AccionMesaCompletada) {
        mostrarSnackbar(textoExitoAccion(evento))
    }

    fun mostrarSesionExpirada() {
        mostrarDialogo(
            titulo = fragment.getString(R.string.sesion_expirada_titulo),
            mensaje = fragment.getString(R.string.sesion_expirada),
            cancelable = false,
            iconoRes = R.drawable.ic_lock,
            alAceptar = alAceptarSesionExpirada
        )
    }

    /** Fallo de red del refresco automatico: aviso discreto, sin tapar el mapa. */
    fun mostrarErrorPolling(codigo: String) {
        val texto = when (codigo) {
            MesasViewModel.ERROR_RED -> fragment.getString(R.string.mesas_polling_sin_conexion)
            else -> fragment.getString(R.string.mesas_polling_error)
        }
        mostrarSnackbar(texto)
    }

    fun mostrarSnackbar(texto: String) {
        val snackbar = Snackbar.make(fragment.requireView(), texto, Snackbar.LENGTH_SHORT)
        snackbar.setTextColor(
            ContextCompat.getColor(fragment.requireContext(), R.color.on_primary)
        )
        snackbar.show()
        snackbar.view.background =
            ContextCompat.getDrawable(fragment.requireContext(), R.drawable.bg_snackbar)
    }

    /** Cierra el aviso abierto, si lo hay. */
    fun cerrar() {
        dialogo?.dismiss()
        dialogo = null
    }

    private fun mostrarAvisoAccion(tipo: AvisoMesas) {
        when (tipo) {
            AvisoMesas.SIN_PLATOS_DIVIDIR -> mostrarDialogo(
                titulo = fragment.getString(R.string.accion_sin_platos_titulo),
                mensaje = fragment.getString(R.string.division_sin_platos)
            )

            AvisoMesas.SIN_DESTINOS -> mostrarDialogo(
                titulo = fragment.getString(R.string.accion_sin_destinos_titulo),
                mensaje = fragment.getString(R.string.accion_sin_destinos)
            )

            AvisoMesas.CONFLICTO_ACCION -> mostrarDialogo(
                titulo = fragment.getString(R.string.accion_dialogo_titulo),
                mensaje = fragment.getString(R.string.accion_error_conflicto),
                iconoRes = R.drawable.ic_error_outline
            )

            else -> mostrarDialogo(
                titulo = fragment.getString(R.string.accion_dialogo_titulo),
                mensaje = fragment.getString(R.string.accion_error_generica)
            )
        }
    }

    private fun mostrarAvisoMesaNoLibre(mesa: Mesa?) {
        if (mesa == null) {
            mostrarDialogo(
                titulo = fragment.getString(R.string.mesas_titulo),
                mensaje = fragment.getString(R.string.error_generico)
            )
        } else {
            mostrarDialogo(
                titulo = fragment.getString(R.string.dialogo_mesa_titulo, mesa.numero),
                mensaje = fragment.getString(mesa.mensajeEstadoRes(), mesa.numero),
                iconoRes = mesa.estado.iconoRes()
            )
        }
    }

    private fun mostrarDialogo(
        titulo: CharSequence,
        mensaje: CharSequence,
        cancelable: Boolean = true,
        @DrawableRes iconoRes: Int = R.drawable.ic_error_outline,
        alAceptar: (() -> Unit)? = null
    ) {
        cerrar()
        val contenido = DialogAvisoMesaBinding.inflate(fragment.layoutInflater)
        contenido.iconoAviso.setImageResource(iconoRes)
        contenido.iconoAviso.contentDescription = titulo
        contenido.tituloAviso.text = titulo
        contenido.mensajeAviso.text = mensaje

        val alerta = MaterialAlertDialogBuilder(fragment.requireContext())
            .setView(contenido.root)
            .setCancelable(cancelable)
            .create()

        contenido.btnEntendido.setOnClickListener {
            alerta.dismiss()
            alAceptar?.invoke()
        }
        alerta.setCanceledOnTouchOutside(cancelable)
        alerta.setOnDismissListener { if (dialogo === alerta) dialogo = null }
        alerta.show()
        dialogo = alerta
    }

    private fun textoExitoAccion(evento: MesasEvento.AccionMesaCompletada): String {
        val destino = evento.mesaDestino?.numero ?: evento.mesaOrigen.numero
        return when (evento.accion) {
            AccionMesa.DIVIDIR -> {
                val platos = evento.platos ?: 0
                fragment.resources.getQuantityString(
                    R.plurals.accion_exito_dividir,
                    platos,
                    platos,
                    destino
                )
            }

            AccionMesa.FUSIONAR -> fragment.getString(
                R.string.accion_exito_fusionar,
                evento.mesaOrigen.numero,
                destino
            )

            AccionMesa.TRANSFERIR -> fragment.getString(
                R.string.accion_exito_transferir,
                evento.mesaOrigen.numero,
                destino
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
}
