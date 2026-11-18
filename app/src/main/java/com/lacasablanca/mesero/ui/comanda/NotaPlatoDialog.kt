package com.lacasablanca.mesero.ui.comanda

import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lacasablanca.mesero.R
import com.lacasablanca.mesero.databinding.DialogNotaPlatoBinding
import com.lacasablanca.mesero.domain.model.ItemCarrito
import com.lacasablanca.mesero.ui.common.aplicarMarcoMarea

/** Edicion de la nota libre de una linea del carrito (RF-06). */
class NotaPlatoDialog(
    private val activity: AppCompatActivity,
    private val item: ItemCarrito,
    private val alGuardar: (String) -> Unit
) {

    private var dialogo: AlertDialog? = null

    fun mostrar() {
        val contenido = DialogNotaPlatoBinding.inflate(activity.layoutInflater)
        contenido.tituloNotaPlato.text = activity.getString(
            R.string.comanda_nota_titulo,
            item.producto.nombre
        )
        contenido.textoNota.setText(item.notas)
        contenido.textoNota.setSelection(item.notas.length)

        val nuevo = MaterialAlertDialogBuilder(activity)
            .setView(contenido.root)
            .setCancelable(true)
            .create()

        contenido.btnGuardarNota.setOnClickListener {
            nuevo.dismiss()
            alGuardar(contenido.textoNota.text?.toString().orEmpty())
        }
        contenido.btnCancelarNota.setOnClickListener { nuevo.dismiss() }

        nuevo.setCanceledOnTouchOutside(true)
        nuevo.setOnDismissListener { if (dialogo === nuevo) dialogo = null }
        nuevo.show()
        nuevo.aplicarMarcoMarea(activity)
        nuevo.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        contenido.textoNota.requestFocus()
        dialogo = nuevo
    }

    fun cerrar() {
        dialogo?.dismiss()
        dialogo = null
    }
}
