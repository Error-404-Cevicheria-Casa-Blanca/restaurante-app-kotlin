package com.lacasablanca.mesero.ui.comanda

import android.content.res.ColorStateList
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.ImageViewCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lacasablanca.mesero.R
import com.lacasablanca.mesero.databinding.DialogAvisoMesaBinding
import com.lacasablanca.mesero.ui.common.aplicarMarcoMarea

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
        val contenido = DialogAvisoMesaBinding.inflate(activity.layoutInflater)
        contenido.tituloAviso.text = titulo
        contenido.mensajeAviso.text = mensaje
        contenido.btnEntendido.setText(textoPositivo)
        configurarIcono(activity, contenido, titulo, cancelable, textoNegativo)

        val dialogo = MaterialAlertDialogBuilder(activity)
            .setView(contenido.root)
            .setCancelable(cancelable)
            .create()

        contenido.btnEntendido.setOnClickListener {
            dialogo.dismiss()
            alAceptar?.invoke()
        }
        if (textoNegativo != null) {
            contenido.btnSecundarioAviso.isVisible = true
            contenido.btnSecundarioAviso.setText(textoNegativo)
            contenido.btnSecundarioAviso.setOnClickListener {
                dialogo.dismiss()
                alCancelar?.invoke()
            }
        }
        dialogo.setCanceledOnTouchOutside(cancelable)
        dialogo.setOnShowListener { dialogo.aplicarMarcoMarea(activity) }
        return dialogo
    }

    private fun configurarIcono(
        activity: AppCompatActivity,
        contenido: DialogAvisoMesaBinding,
        titulo: CharSequence,
        cancelable: Boolean,
        textoNegativo: Int?
    ) {
        val esConfirmacion = textoNegativo != null || !cancelable
        val textoTitulo = titulo.toString()
        val esError = !esConfirmacion && (
            textoTitulo.contains("error", ignoreCase = true) ||
                textoTitulo.contains("no se pudo", ignoreCase = true)
            )
        val (fondoRes, iconoRes, colorRes) = if (esError) {
            Triple(R.drawable.bg_ib_err, R.drawable.ic_error_outline, R.color.sem_err_c)
        } else {
            Triple(R.drawable.bg_ib_warn, R.drawable.ic_alert_triangle, R.color.sem_warn_c)
        }
        contenido.badgeIconoAviso.setBackgroundResource(fondoRes)
        contenido.iconoAviso.setImageResource(iconoRes)
        contenido.iconoAviso.contentDescription = titulo
        ImageViewCompat.setImageTintList(
            contenido.iconoAviso,
            ColorStateList.valueOf(ContextCompat.getColor(activity, colorRes))
        )
    }
}
