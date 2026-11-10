package com.lacasablanca.mesero.ui.comanda

import android.graphics.text.LineBreaker
import android.text.Layout
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.lacasablanca.mesero.R
import com.lacasablanca.mesero.domain.model.Categoria

/**
 * Chips de categoria del catalogo. Solo se reconstruyen cuando cambian las
 * categorias o la seleccion, para no parpadear con cada cambio del carrito.
 */
class CatalogoChips(
    private val activity: AppCompatActivity,
    private val grupo: ChipGroup,
    private val alFiltrar: (Long?) -> Unit
) {

    private var categoriasRenderizadas: List<Categoria>? = null
    private var seleccionRenderizada: Long? = null

    fun render(categorias: List<Categoria>, seleccionada: Long?) {
        if (categorias == categoriasRenderizadas &&
            seleccionada == seleccionRenderizada &&
            grupo.childCount > 0
        ) {
            return
        }

        categoriasRenderizadas = categorias
        seleccionRenderizada = seleccionada
        grupo.removeAllViews()

        grupo.addView(
            crearChip(
                texto = activity.getString(R.string.comanda_categoria_todas),
                categoriaId = null,
                marcado = seleccionada == null
            )
        )
        categorias.forEach { categoria ->
            grupo.addView(
                crearChip(
                    texto = categoria.nombre,
                    categoriaId = categoria.id,
                    marcado = seleccionada == categoria.id
                )
            )
        }
    }

    private fun crearChip(texto: String, categoriaId: Long?, marcado: Boolean): Chip {
        val chip = Chip(activity)
        chip.id = View.generateViewId()
        chip.text = texto
        chip.isCheckable = true
        chip.isChecked = marcado
        chip.isClickable = true
        chip.setEnsureMinTouchTargetSize(true)
        chip.minHeight = activity.resources.getDimensionPixelSize(R.dimen.toque_minimo)
        chip.chipStrokeWidth = 0f
        chip.setChipBackgroundColorResource(R.color.selector_fondo_chip_categoria)
        chip.setTextColor(
            ContextCompat.getColorStateList(activity, R.color.selector_texto_chip_categoria)
        )
        chip.setBreakStrategy(LineBreaker.BREAK_STRATEGY_SIMPLE)
        chip.hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NONE
        chip.contentDescription = activity.getString(R.string.cd_categoria, texto)
        chip.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) alFiltrar(categoriaId)
        }
        return chip
    }
}
