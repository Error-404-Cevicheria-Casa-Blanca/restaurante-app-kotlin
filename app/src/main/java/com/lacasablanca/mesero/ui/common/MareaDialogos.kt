package com.lacasablanca.mesero.ui.common

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog

/**
 * Configura fondo transparente de ventana, scrim atenuado y ancho acotado
 * para que todos los dialogos muestren exactamente el marco Marea (`bg_dialogo_premium`).
 */
internal fun AlertDialog.aplicarMarcoMarea(
    context: Context,
    maxAnchoDp: Int = ANCHO_DIALOGO_MAREA_DP
) {
    window?.apply {
        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        setDimAmount(SCRIM_DIALOGO_MAREA)
        val densidad = context.resources.displayMetrics.density
        val anchoPantalla = (context.resources.displayMetrics.widthPixels * FRACCION_ANCHO_MAREA).toInt()
        val anchoMaximo = (maxAnchoDp * densidad).toInt()
        setLayout(minOf(anchoPantalla, anchoMaximo), ViewGroup.LayoutParams.WRAP_CONTENT)
    }
}

private const val ANCHO_DIALOGO_MAREA_DP = 410
private const val FRACCION_ANCHO_MAREA = 0.88f
private const val SCRIM_DIALOGO_MAREA = 0.60f
