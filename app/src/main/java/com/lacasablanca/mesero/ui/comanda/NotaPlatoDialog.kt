package com.lacasablanca.mesero.ui.comanda

import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lacasablanca.mesero.R
import com.lacasablanca.mesero.databinding.DialogNotaPlatoBinding
import com.lacasablanca.mesero.domain.model.ItemCarrito

/** Edicion de la nota libre de una linea del carrito (RF-06). */
class NotaPlatoDialog(
    private val activity: AppCompatActivity,
    private val item: ItemCarrito,
    private val alGuardar: (String) -> Unit
) {

    private var dialogo: AlertDialog? = null

    fun mostrar() {
        val contenido = DialogNotaPlatoBinding.inflate(activity.layoutInflater)
        contenido.textoNota.setText(item.notas)
        contenido.textoNota.setSelection(item.notas.length)

        val nuevo = MaterialAlertDialogBuilder(activity)
            .setTitle(activity.getString(R.string.comanda_nota_titulo, item.producto.nombre))
            .setView(contenido.root)
            .setPositiveButton(R.string.comanda_nota_guardar) { _, _ ->
                alGuardar(contenido.textoNota.text?.toString().orEmpty())
            }
            .setNegativeButton(R.string.accion_cancelar, null)
            .create()

        nuevo.setOnDismissListener { if (dialogo === nuevo) dialogo = null }
        nuevo.show()
        dialogo = nuevo
    }

    fun cerrar() {
        dialogo?.dismiss()
        dialogo = null
    }
}
