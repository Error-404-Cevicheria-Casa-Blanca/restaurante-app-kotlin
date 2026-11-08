package com.lacasablanca.mesero.ui.comanda

import android.app.Activity
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lacasablanca.mesero.R
import com.lacasablanca.mesero.databinding.DialogAnularPlatoBinding
import com.lacasablanca.mesero.domain.model.ItemCarrito
import com.lacasablanca.mesero.ui.common.formatearMoneda

/**
 * Dialogo premium de confirmacion de anulacion (PRB-77).
 *
 * Encapsula el layout, el estado de progreso y el formateo del plato; la
 * Activity solo entrega el callback que llama al ViewModel. Asi la pantalla
 * de comanda no crece con detalle de UI de un solo dialogo.
 */
class AnularPlatoDialog(
    private val activity: Activity,
    private val item: ItemCarrito,
    private val alConfirmar: (ItemCarrito) -> Unit
) {

    private var dialogo: AlertDialog? = null

    /** Muestra el dialogo; si ya estaba abierto, lo reemplaza. */
    fun mostrar() {
        cerrar()

        val vista = DialogAnularPlatoBinding.inflate(
            LayoutInflater.from(activity),
            null,
            false
        )
        vista.textoPlatoAnular.text = item.producto.nombre.ifBlank {
            activity.getString(R.string.comanda_plato_sin_nombre, item.producto.id)
        }
        vista.textoDetalleAnular.text = activity.getString(
            R.string.anular_plato_detalle,
            item.cantidad,
            formatearMoneda(item.producto.precio),
            formatearMoneda(item.subtotal)
        )

        val nuevo = MaterialAlertDialogBuilder(
            activity,
            R.style.ThemeOverlay_LaCasaBlanca_Dialogo_AnularPlato
        )
            .setView(vista.root)
            .setCancelable(true)
            .create()

        vista.btnCancelarAnular.setOnClickListener { nuevo.dismiss() }
        vista.btnConfirmarAnular.setOnClickListener { confirmar(nuevo, vista) }

        nuevo.setCanceledOnTouchOutside(true)
        nuevo.setOnDismissListener {
            if (dialogo === nuevo) dialogo = null
        }
        nuevo.show()

        dialogo = nuevo
    }

    /** Cierra el dialogo si sigue abierto (exito, error o salida de pantalla). */
    fun cerrar() {
        dialogo?.dismiss()
        dialogo = null
    }

    private fun confirmar(dialogo: AlertDialog, vista: DialogAnularPlatoBinding) {
        dialogo.setCancelable(false)
        dialogo.setCanceledOnTouchOutside(false)
        vista.grupoBotonesAnular.isVisible = false
        vista.progresoAnular.isVisible = true
        vista.textoProgresoAnular.isVisible = true
        alConfirmar(item)
    }
}
