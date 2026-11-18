package com.lacasablanca.mesero.ui.common

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import androidx.annotation.ColorInt

/**
 * Fondo de cinta con esquinas izquierdas redondeadas y muesca cola de golondrina (`.rib` en Marea).
 */
class MesaRibbonDrawable(
    private val density: Float,
    @ColorInt private var colorFondo: Int = 0,
    @ColorInt private var colorBorde: Int = 0,
) : Drawable() {

    private val pathExterior = Path()
    private val pathInterior = Path()
    private val arcRect = RectF()

    private val paintExterior = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = colorBorde
    }

    private val paintInterior = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = colorFondo
    }

    fun actualizarColores(@ColorInt fondo: Int, @ColorInt borde: Int) {
        if (colorFondo == fondo && colorBorde == borde) return
        colorFondo = fondo
        colorBorde = borde
        paintInterior.color = fondo
        paintExterior.color = borde
        invalidateSelf()
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        reconstruirTrazos(bounds)
    }

    override fun draw(canvas: Canvas) {
        if (bounds.isEmpty) return
        canvas.drawPath(pathExterior, paintExterior)
        canvas.drawPath(pathInterior, paintInterior)
    }

    override fun setAlpha(alpha: Int) {
        paintExterior.alpha = alpha
        paintInterior.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paintExterior.colorFilter = colorFilter
        paintInterior.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Requerido por Drawable en API < 29")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    private fun reconstruirTrazos(bounds: Rect) {
        val left = bounds.left.toFloat()
        val top = bounds.top.toFloat()
        val right = bounds.right.toFloat()
        val bottom = bounds.bottom.toFloat()
        val ancho = right - left
        val alto = bottom - top
        if (ancho <= 0f || alto <= 0f) {
            pathExterior.reset()
            pathInterior.reset()
            return
        }

        val stroke = STROKE_DP * density
        construirCinta(
            target = pathExterior,
            l = left,
            t = top,
            r = right,
            b = bottom,
            radioIzq = (RADIO_EXT_DP * density).coerceAtMost(alto / 2f),
            muesca = (MUESCA_EXT_DP * density).coerceAtMost(ancho * 0.45f),
        )
        construirCinta(
            target = pathInterior,
            l = left + stroke,
            t = top + stroke,
            r = right,
            b = bottom - stroke,
            radioIzq = (RADIO_INT_DP * density).coerceAtMost((alto - stroke * 2f) / 2f).coerceAtLeast(0f),
            muesca = (MUESCA_INT_DP * density).coerceAtMost(ancho * 0.45f),
        )
    }

    private fun construirCinta(
        target: Path,
        l: Float,
        t: Float,
        r: Float,
        b: Float,
        radioIzq: Float,
        muesca: Float,
    ) {
        target.reset()
        val midY = (t + b) / 2f
        val d = radioIzq * 2f
        target.moveTo(l, t + radioIzq)
        if (radioIzq > 0f) {
            arcRect.set(l, t, l + d, t + d)
            target.arcTo(arcRect, 180f, 90f, false)
        }
        target.lineTo(r, t)
        target.lineTo(r - muesca, midY)
        target.lineTo(r, b)
        target.lineTo(l + radioIzq, b)
        if (radioIzq > 0f) {
            arcRect.set(l, b - d, l + d, b)
            target.arcTo(arcRect, 90f, 90f, false)
        }
        target.close()
    }

    private companion object {
        const val STROKE_DP = 1f
        const val RADIO_EXT_DP = 8f
        const val RADIO_INT_DP = 7f
        const val MUESCA_EXT_DP = 11f
        const val MUESCA_INT_DP = 10.2f
    }
}
