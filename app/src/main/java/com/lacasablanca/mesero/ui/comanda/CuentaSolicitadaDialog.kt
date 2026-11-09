package com.lacasablanca.mesero.ui.comanda

import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lacasablanca.mesero.R
import com.lacasablanca.mesero.databinding.DialogCuentaSolicitadaBinding

/** Exito de "Pedir Cuenta" (PRB-83); al volver se cierra la comanda. */
class CuentaSolicitadaDialog(
    private val activity: AppCompatActivity,
    private val numeroMesa: Int,
    private val alVolver: () -> Unit
) {

    private var dialogo: AlertDialog? = null

    fun mostrar() {
        val contenido = DialogCuentaSolicitadaBinding.inflate(activity.layoutInflater)
        contenido.textoCuentaMensaje.text = activity.getString(R.string.comanda_cuenta_mensaje)
        contenido.textoCuentaDetalle.text = activity.getString(
            R.string.comanda_cuenta_detalle,
            numeroMesa
        )

        val nuevo = MaterialAlertDialogBuilder(activity)
            .setView(contenido.root)
            .setCancelable(false)
            .create()

        contenido.btnVolverMapaCuenta.setOnClickListener {
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
}
