package com.lacasablanca.mesero.ui.comanda

import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lacasablanca.mesero.R

/** Dialogos simples de la comanda (avisos y confirmaciones). */
object ComandaDialogos {

    fun crear(
        activity: AppCompatActivity,
        titulo: CharSequence,
        mensaje: CharSequence,
        cancelable: Boolean,
        textoPositivo: Int = R.string.accion_aceptar,
        alAceptar: (() -> Unit)? = null,
        textoNegativo: Int? = null,
        alCancelar: (() -> Unit)? = null
    ): AlertDialog {
        val constructor = MaterialAlertDialogBuilder(activity)
            .setTitle(titulo)
            .setMessage(mensaje)
            .setCancelable(cancelable)
            .setPositiveButton(textoPositivo) { dialog, _ ->
                dialog.dismiss()
                alAceptar?.invoke()
            }
        if (textoNegativo != null) {
            constructor.setNegativeButton(textoNegativo) { dialog, _ ->
                dialog.dismiss()
                alCancelar?.invoke()
            }
        }
        val dialogo = constructor.create()
        dialogo.setCanceledOnTouchOutside(cancelable)
        return dialogo
    }
}
