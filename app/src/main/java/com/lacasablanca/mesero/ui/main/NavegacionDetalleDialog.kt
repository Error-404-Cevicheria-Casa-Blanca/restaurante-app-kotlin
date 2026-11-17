package com.lacasablanca.mesero.ui.main

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

/**
 * Muestra el dialogo Marea cuando existe un cobro o comanda oculto en otra
 * pestana y el mesero intenta abrir una nueva operacion.
 */
internal fun mostrarAvisoDetallePendiente(
    activity: AppCompatActivity,
    esCobro: Boolean,
    nombreTabPendiente: String,
    alVolver: () -> Unit,
    alDescartar: () -> Unit,
    alCerrar: () -> Unit
): AlertDialog {
    val binding = DialogAvisoMesaBinding.inflate(activity.layoutInflater)
    binding.badgeIconoAviso.setBackgroundResource(R.drawable.bg_ib_warn)
    binding.iconoAviso.setImageResource(R.drawable.ic_alert_triangle)
    ImageViewCompat.setImageTintList(
        binding.iconoAviso,
        ColorStateList.valueOf(ContextCompat.getColor(activity, R.color.sem_warn_c))
    )
    binding.tituloAviso.setText(
        if (esCobro) {
            R.string.nav_detalle_pendiente_cobro_titulo
        } else {
            R.string.nav_detalle_pendiente_comanda_titulo
        }
    )
    binding.mensajeAviso.text = activity.getString(
        R.string.nav_detalle_pendiente_mensaje,
        nombreTabPendiente
    )
    binding.btnEntendido.setText(R.string.nav_detalle_ir_pendiente)
    binding.btnSecundarioAviso.isVisible = true
    binding.btnSecundarioAviso.setText(R.string.nav_detalle_descartar)

    val dialogo = MaterialAlertDialogBuilder(
        activity,
        R.style.ThemeOverlay_LaCasaBlanca_Dialogo_LiberarMesa
    )
        .setView(binding.root)
        .setCancelable(true)
        .create()

    binding.btnEntendido.setOnClickListener {
        dialogo.dismiss()
        alVolver()
    }
    binding.btnSecundarioAviso.setOnClickListener {
        dialogo.dismiss()
        alDescartar()
    }
    dialogo.setCanceledOnTouchOutside(true)
    dialogo.setOnDismissListener { alCerrar() }
    dialogo.show()
    dialogo.aplicarMarcoMarea(activity)
    return dialogo
}
