package com.lacasablanca.mesero.ui.mesas

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import com.lacasablanca.mesero.R
import com.lacasablanca.mesero.databinding.ItemMesaBinding
import com.lacasablanca.mesero.domain.model.EstadoMesa
import com.lacasablanca.mesero.domain.model.Mesa
import com.lacasablanca.mesero.ui.common.MesaRibbonDrawable
import com.lacasablanca.mesero.ui.common.colorRes
import com.lacasablanca.mesero.ui.common.etiquetaRes
import com.lacasablanca.mesero.ui.common.iconoRes
import kotlin.math.roundToInt

/**
 * Aplica el estilo visual Marea (degradado de tarjeta, croquis cenital y cinta con muesca) a un ítem de mesa.
 */
internal object MesaTarjetaMarea {

    fun vincular(
        binding: ItemMesaBinding,
        mesa: Mesa,
        alTocar: (Mesa) -> Unit,
        alAccionar: (Mesa) -> Unit,
    ) {
        val contexto = binding.root.context
        val densidad = contexto.resources.displayMetrics.density
        val paleta = paletaParaEstado(mesa.estado)

        val colorAcento = ContextCompat.getColor(contexto, mesa.estado.colorRes())
        val colorTop = ContextCompat.getColor(contexto, paleta.fondoTopRes)
        val colorBottom = ContextCompat.getColor(contexto, paleta.fondoBottomRes)
        val colorBorde = ContextCompat.getColor(contexto, paleta.bordeRes)
        val colorCintaFondo = ContextCompat.getColor(contexto, paleta.cintaFondoRes)
        val colorCintaBorde = ContextCompat.getColor(contexto, paleta.cintaBordeRes)

        val etiqueta = contexto.getString(mesa.estado.etiquetaRes())
        val capacidadTexto = contexto.resources.getQuantityString(
            R.plurals.mesa_capacidad,
            mesa.capacidad,
            mesa.capacidad,
        )

        binding.textoMesa.text = contexto.getString(R.string.mesa_titulo, mesa.numero)
        binding.textoNumeroMesa.text = mesa.numero.toString()
        binding.textoCapacidad.text = mesa.capacidad.toString()

        binding.croquisMesa.capacidad = mesa.capacidad
        binding.croquisMesa.colorAcento = colorAcento

        val fondoGradiente = (binding.contenidoMesa.background as? GradientDrawable)
            ?: GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(colorTop, colorBottom)).also {
                binding.contenidoMesa.background = it
            }
        fondoGradiente.colors = intArrayOf(colorTop, colorBottom)

        binding.cardMesa.strokeColor = colorBorde
        binding.cardMesa.strokeWidth = (paleta.strokeDp * densidad).roundToInt().coerceAtLeast(1)

        val ribbon = (binding.ribbonEstado.background as? MesaRibbonDrawable)
            ?: MesaRibbonDrawable(densidad).also { binding.ribbonEstado.background = it }
        ribbon.actualizarColores(colorCintaFondo, colorCintaBorde)

        binding.iconoEstado.setImageResource(mesa.estado.iconoRes())
        binding.iconoEstado.imageTintList = ColorStateList.valueOf(colorAcento)
        binding.iconoEstado.contentDescription = etiqueta

        binding.textoEstado.text = etiqueta
        binding.textoEstado.setTextColor(colorAcento)

        binding.cardMesa.contentDescription = contexto.getString(
            R.string.mesa_descripcion,
            mesa.numero,
            capacidadTexto,
            etiqueta,
        )
        binding.btnAccionesMesa.contentDescription = contexto.getString(
            R.string.cd_acciones_mesa,
            mesa.numero,
        )

        binding.cardMesa.setOnClickListener { alTocar(mesa) }
        binding.cardMesa.setOnLongClickListener {
            alAccionar(mesa)
            true
        }
        binding.btnAccionesMesa.setOnClickListener { alAccionar(mesa) }
    }

    private data class PaletaEstado(
        @ColorRes val fondoTopRes: Int,
        @ColorRes val fondoBottomRes: Int,
        @ColorRes val bordeRes: Int,
        @ColorRes val cintaFondoRes: Int,
        @ColorRes val cintaBordeRes: Int,
        val strokeDp: Float,
    )

    private fun paletaParaEstado(estado: EstadoMesa): PaletaEstado = when (estado) {
        EstadoMesa.LIBRE -> PALETA_LIBRE
        EstadoMesa.ATENDIDA -> PALETA_ATENDIDA
        EstadoMesa.OCUPADA -> PALETA_OCUPADA
        EstadoMesa.CUENTA_SOLICITADA -> PALETA_CUENTA
        EstadoMesa.DESCONOCIDO -> PALETA_DESCONOCIDA
    }

    private val PALETA_LIBRE = PaletaEstado(
        fondoTopRes = R.color.mesa_card_libre_top,
        fondoBottomRes = R.color.mesa_card_libre_bottom,
        bordeRes = R.color.mesa_libre_cb,
        cintaFondoRes = R.color.mesa_libre_cf,
        cintaBordeRes = R.color.mesa_libre_cbs,
        strokeDp = 1f,
    )

    private val PALETA_ATENDIDA = PaletaEstado(
        fondoTopRes = R.color.mesa_card_aten_top,
        fondoBottomRes = R.color.mesa_card_aten_bottom,
        bordeRes = R.color.estado_atencion,
        cintaFondoRes = R.color.mesa_aten_cf,
        cintaBordeRes = R.color.mesa_aten_cbs,
        strokeDp = 2f,
    )

    private val PALETA_OCUPADA = PaletaEstado(
        fondoTopRes = R.color.mesa_card_ocu_top,
        fondoBottomRes = R.color.mesa_card_ocu_bottom,
        bordeRes = R.color.mesa_ocu_cb,
        cintaFondoRes = R.color.mesa_ocu_cf,
        cintaBordeRes = R.color.mesa_ocu_cbs,
        strokeDp = 1f,
    )

    private val PALETA_CUENTA = PaletaEstado(
        fondoTopRes = R.color.mesa_card_cta_top,
        fondoBottomRes = R.color.mesa_card_cta_bottom,
        bordeRes = R.color.mesa_cta_cb,
        cintaFondoRes = R.color.mesa_cta_cf,
        cintaBordeRes = R.color.mesa_cta_cbs,
        strokeDp = 1f,
    )

    private val PALETA_DESCONOCIDA = PaletaEstado(
        fondoTopRes = R.color.mesa_card_desc_top,
        fondoBottomRes = R.color.mesa_card_desc_bottom,
        bordeRes = R.color.mesa_desc_cb,
        cintaFondoRes = R.color.mesa_desc_cf,
        cintaBordeRes = R.color.mesa_desc_cbs,
        strokeDp = 1f,
    )
}
