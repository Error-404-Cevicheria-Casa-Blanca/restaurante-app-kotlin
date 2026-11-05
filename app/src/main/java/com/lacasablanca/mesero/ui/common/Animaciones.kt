package com.lacasablanca.mesero.ui.common

import android.view.View
import android.view.animation.DecelerateInterpolator

private const val DESPLAZAMIENTO_DP = 14f
private const val DURACION_MS = 240L

/**
 * Aparicion suave para la primera carga de contenido: fade-in + slide-up.
 *
 * Solo modifica propiedades de dibujo ([View.getAlpha] y
 * [View.getTranslationY]); no toca el layout ni el estado del ViewModel, y
 * deja la vista en su posicion final al terminar.
 */
fun View.aparecerConDesplazamiento(
    desplazamientoDp: Float = DESPLAZAMIENTO_DP,
    duracionMs: Long = DURACION_MS
) {
    animate().cancel()
    val desplazamiento = resources.displayMetrics.density * desplazamientoDp
    alpha = 0f
    translationY = desplazamiento
    animate()
        .alpha(1f)
        .translationY(0f)
        .setDuration(duracionMs)
        .setInterpolator(DecelerateInterpolator())
        .start()
}
