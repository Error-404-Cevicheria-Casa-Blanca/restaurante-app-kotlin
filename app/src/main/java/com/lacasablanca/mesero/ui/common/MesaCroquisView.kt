package com.lacasablanca.mesero.ui.common

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import com.lacasablanca.mesero.R
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Croquis cenital de una mesa y sus sillas según capacidad (diseño Marea `.plan2`).
 */
class MesaCroquisView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val rectDibujo = RectF()

    private val paintMesa = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = ContextCompat.getColor(context, R.color.estado_libre)
    }

    private val paintSilla = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = paintMesa.color
    }

    var capacidad: Int = 4
        set(value) {
            val normalizada = value.coerceAtLeast(1)
            if (field != normalizada) {
                field = normalizada
                invalidate()
            }
        }

    @ColorInt
    var colorAcento: Int = paintMesa.color
        set(value) {
            if (field != value) {
                field = value
                paintMesa.color = value
                paintSilla.color = value
                invalidate()
            }
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val anchoDisponible = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        val altoDisponible = MeasureSpec.getSize(heightMeasureSpec) - paddingTop - paddingBottom
        val modoAncho = MeasureSpec.getMode(widthMeasureSpec)
        val modoAlto = MeasureSpec.getMode(heightMeasureSpec)

        val deseado = calcularTamano(anchoDisponible, altoDisponible, modoAncho, modoAlto)
        setMeasuredDimension(
            resolveSize(deseado.first + paddingLeft + paddingRight, widthMeasureSpec),
            resolveSize(deseado.second + paddingTop + paddingBottom, heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val contenidoW = (width - paddingLeft - paddingRight).toFloat()
        val contenidoH = (height - paddingTop - paddingBottom).toFloat()
        if (contenidoW <= 0f || contenidoH <= 0f) return

        val escala = min(contenidoW / VIEWBOX_ANCHO, contenidoH / VIEWBOX_ALTO)
        val offsetX = paddingLeft + (contenidoW - VIEWBOX_ANCHO * escala) / 2f
        val offsetY = paddingTop + (contenidoH - VIEWBOX_ALTO * escala) / 2f

        val trazoBase = if (esModoOscuro()) TRAZO_OSCURO else TRAZO_CLARO
        paintMesa.strokeWidth = trazoBase * escala

        val geometria = geometriaParaCapacidad(capacidad)
        dibujarRectangulo(canvas, geometria.mesa, offsetX, offsetY, escala, paintMesa)
        geometria.sillas.forEach { silla ->
            dibujarRectangulo(canvas, silla, offsetX, offsetY, escala, paintSilla)
        }
    }

    private fun calcularTamano(
        anchoDisponible: Int,
        altoDisponible: Int,
        modoAncho: Int,
        modoAlto: Int,
    ): Pair<Int, Int> {
        val ratio = VIEWBOX_ANCHO / VIEWBOX_ALTO
        return when {
            modoAlto == MeasureSpec.EXACTLY && altoDisponible > 0 -> {
                val alto = altoDisponible
                val ancho = (alto * ratio).roundToInt()
                val anchoFinal = if (modoAncho == MeasureSpec.UNSPECIFIED) ancho else min(ancho, anchoDisponible)
                anchoFinal to alto
            }
            modoAncho == MeasureSpec.EXACTLY && anchoDisponible > 0 -> {
                val ancho = anchoDisponible
                val alto = (ancho / ratio).roundToInt()
                val altoFinal = if (modoAlto == MeasureSpec.UNSPECIFIED) alto else min(alto, altoDisponible)
                ancho to altoFinal
            }
            else -> {
                val maxAltoPx = (ALTO_BASE_DP * resources.displayMetrics.density).roundToInt()
                val alto = if (modoAlto == MeasureSpec.AT_MOST) min(maxAltoPx, altoDisponible) else maxAltoPx
                val ancho = (alto * ratio).roundToInt()
                val anchoFinal = if (modoAncho == MeasureSpec.AT_MOST) min(ancho, anchoDisponible) else ancho
                val altoFinal = (anchoFinal / ratio).roundToInt()
                anchoFinal to altoFinal
            }
        }
    }

    private fun dibujarRectangulo(
        canvas: Canvas,
        spec: RectSpec,
        offsetX: Float,
        offsetY: Float,
        escala: Float,
        paint: Paint,
    ) {
        rectDibujo.set(
            offsetX + spec.x * escala,
            offsetY + spec.y * escala,
            offsetX + (spec.x + spec.w) * escala,
            offsetY + (spec.y + spec.h) * escala,
        )
        val radio = spec.rx * escala
        canvas.drawRoundRect(rectDibujo, radio, radio, paint)
    }

    private fun esModoOscuro(): Boolean {
        val mascara = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return mascara == Configuration.UI_MODE_NIGHT_YES
    }

    private data class RectSpec(val x: Float, val y: Float, val w: Float, val h: Float, val rx: Float)
    private data class CroquisGeometria(val mesa: RectSpec, val sillas: List<RectSpec>)

    private companion object {
        const val VIEWBOX_ANCHO = 112f
        const val VIEWBOX_ALTO = 84f
        const val ALTO_BASE_DP = 76f
        const val TRAZO_OSCURO = 2.2f
        const val TRAZO_CLARO = 2.4f

        val CROQUIS_2 = CroquisGeometria(
            mesa = RectSpec(36f, 24f, 40f, 36f, 9f),
            sillas = listOf(
                RectSpec(20f, 35f, 10f, 14f, 4f),
                RectSpec(82f, 35f, 10f, 14f, 4f),
            ),
        )

        val CROQUIS_4 = CroquisGeometria(
            mesa = RectSpec(26f, 24f, 60f, 36f, 9f),
            sillas = listOf(
                RectSpec(36f, 10f, 14f, 10f, 4f),
                RectSpec(62f, 10f, 14f, 10f, 4f),
                RectSpec(36f, 64f, 14f, 10f, 4f),
                RectSpec(62f, 64f, 14f, 10f, 4f),
            ),
        )

        val CROQUIS_6 = CroquisGeometria(
            mesa = RectSpec(18f, 24f, 76f, 36f, 9f),
            sillas = listOf(
                RectSpec(30f, 10f, 14f, 10f, 4f),
                RectSpec(68f, 10f, 14f, 10f, 4f),
                RectSpec(30f, 64f, 14f, 10f, 4f),
                RectSpec(68f, 64f, 14f, 10f, 4f),
                RectSpec(4f, 35f, 10f, 14f, 4f),
                RectSpec(98f, 35f, 10f, 14f, 4f),
            ),
        )

        val CROQUIS_8 = CroquisGeometria(
            mesa = RectSpec(14f, 24f, 84f, 36f, 9f),
            sillas = listOf(
                RectSpec(22f, 10f, 14f, 10f, 4f),
                RectSpec(49f, 10f, 14f, 10f, 4f),
                RectSpec(76f, 10f, 14f, 10f, 4f),
                RectSpec(22f, 64f, 14f, 10f, 4f),
                RectSpec(49f, 64f, 14f, 10f, 4f),
                RectSpec(76f, 64f, 14f, 10f, 4f),
                RectSpec(2f, 35f, 10f, 14f, 4f),
                RectSpec(100f, 35f, 10f, 14f, 4f),
            ),
        )

        fun geometriaParaCapacidad(capacidad: Int): CroquisGeometria = when {
            capacidad <= 2 -> CROQUIS_2
            capacidad <= 4 -> CROQUIS_4
            capacidad <= 6 -> CROQUIS_6
            else -> CROQUIS_8
        }
    }
}
