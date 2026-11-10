package com.lacasablanca.mesero.ui.comanda

import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lacasablanca.mesero.R
import com.lacasablanca.mesero.databinding.DialogPedidoEnviadoBinding
import com.lacasablanca.mesero.domain.model.PedidoEnviado
import com.lacasablanca.mesero.ui.common.formatearMoneda

/**
 * Dialogo de exito del pedido (PRB-38) con la variante para llevar (PRB-94).
 * Se extrajo de [ComandaFragment] para mantener la pantalla por debajo del
 * umbral de tamano de detekt.
 */
class PedidoEnviadoDialog(
    private val activity: AppCompatActivity,
    private val paraLlevar: Boolean,
    private val clienteNombre: String,
    private val numeroMesa: Int,
    private val pedido: PedidoEnviado,
    private val alVolver: () -> Unit
) {

    private var dialogo: AlertDialog? = null

    fun mostrar() {
        val contenido = DialogPedidoEnviadoBinding.inflate(activity.layoutInflater)
        contenido.textoExitoMensaje.text = mensajePedido()
        contenido.textoExitoTotal.text = activity.getString(
            R.string.comanda_pedido_enviado_total,
            formatearMoneda(pedido.total)
        )

        val nuevo = MaterialAlertDialogBuilder(activity)
            .setView(contenido.root)
            .setCancelable(false)
            .create()

        contenido.btnVolverMapa.setOnClickListener {
            nuevo.dismiss()
            alVolver()
        }

        nuevo.setCanceledOnTouchOutside(false)
        nuevo.setOnDismissListener { if (dialogo === nuevo) dialogo = null }
        nuevo.show()
        dialogo = nuevo
    }

    fun cerrar() {
        dialogo?.dismiss()
        dialogo = null
    }

    private fun mensajePedido(): String = if (paraLlevar) {
        activity.getString(
            R.string.comanda_pedido_enviado_mensaje_llevar,
            clienteNombre,
            pedido.id
        )
    } else {
        activity.getString(
            R.string.comanda_pedido_enviado_mensaje,
            numeroMesa,
            pedido.id
        )
    }
}
