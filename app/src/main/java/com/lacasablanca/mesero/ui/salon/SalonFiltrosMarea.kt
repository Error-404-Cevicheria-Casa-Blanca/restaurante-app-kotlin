package com.lacasablanca.mesero.ui.salon

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.google.android.material.tabs.TabLayout
import com.lacasablanca.mesero.R
import com.lacasablanca.mesero.databinding.ItemChipSalonBinding
import com.lacasablanca.mesero.databinding.ItemLeyendaBinding
import com.lacasablanca.mesero.domain.model.EstadoMesa
import com.lacasablanca.mesero.ui.common.colorRes
import com.lacasablanca.mesero.ui.common.etiquetaChipTabletRes
import com.lacasablanca.mesero.ui.common.etiquetaRes
import com.lacasablanca.mesero.ui.common.iconoRes
import com.lacasablanca.mesero.ui.mesas.MesasUiState
import kotlin.math.roundToInt

/**
 * Construye pestañas segmentadas de zona, chips Marea y leyenda de estados del Salón.
 */
internal object SalonFiltrosMarea {

    private val ESTADOS_CHIP = listOf(
        EstadoMesa.LIBRE,
        EstadoMesa.OCUPADA,
        EstadoMesa.CUENTA_SOLICITADA,
        EstadoMesa.ATENDIDA,
    )

    private val ESTADOS_LEYENDA = listOf(
        EstadoMesa.LIBRE,
        EstadoMesa.ATENDIDA,
        EstadoMesa.OCUPADA,
        EstadoMesa.CUENTA_SOLICITADA,
    )

    fun renderTabsZonas(
        tabsZonas: TabLayout,
        estado: MesasUiState,
    ) {
        val contexto = tabsZonas.context
        val conteoPorZona = estado.mesas.groupingBy { it.ubicacion }.eachCount()

        tabsZonas.removeAllTabs()
        tabsZonas.addTab(
            tabsZonas.newTab()
                .setText(R.string.zona_todas)
                .setTag(null),
        )
        estado.zonas.forEach { zona ->
            val textoTab = contexto.getString(
                R.string.zona_con_contador,
                zona,
                conteoPorZona[zona] ?: 0,
            )
            tabsZonas.addTab(
                tabsZonas.newTab()
                    .setText(textoTab)
                    .setTag(zona),
            )
        }

        val indiceActivo = if (estado.zonaSeleccionada == null) {
            0
        } else {
            val pos = estado.zonas.indexOf(estado.zonaSeleccionada)
            if (pos >= 0) pos + 1 else 0
        }
        tabsZonas.getTabAt(indiceActivo)?.select()
    }

    fun renderChipsEstados(
        contenedor: LinearLayout,
        inflater: LayoutInflater,
        estado: MesasUiState,
        alSeleccionar: (EstadoMesa?) -> Unit,
    ) {
        val contexto = contenedor.context
        val esTablet = contexto.resources.configuration.smallestScreenWidthDp >= 600
        val densidad = contexto.resources.displayMetrics.density
        val altoPx = (44f * densidad).roundToInt()
        val gapPx = ((if (esTablet) 8f else 6f) * densidad).roundToInt()

        contenedor.removeAllViews()

        val items: List<EstadoMesa?> = listOf(null) + ESTADOS_CHIP
        items.forEachIndexed { indice, filtroEstado ->
            val seleccionado = estado.estadoSeleccionado == filtroEstado
            val chipBinding = ItemChipSalonBinding.inflate(inflater, contenedor, false)
            configurarChip(
                binding = chipBinding,
                filtroEstado = filtroEstado,
                total = if (filtroEstado == null) estado.totalMesas else (estado.contadores[filtroEstado] ?: 0),
                seleccionado = seleccionado,
                esTablet = esTablet,
                densidad = densidad,
            )
            chipBinding.root.layoutParams = crearLayoutParamsChip(
                esTodas = filtroEstado == null,
                esTablet = esTablet,
                altoPx = altoPx,
                margenFinPx = if (indice < items.lastIndex) gapPx else 0,
            )
            chipBinding.root.setOnClickListener {
                val nuevoFiltro = if (filtroEstado != null && seleccionado) null else filtroEstado
                alSeleccionar(nuevoFiltro)
            }
            contenedor.addView(chipBinding.root)
        }
    }

    fun pintarLeyenda(contenedor: LinearLayout, inflater: LayoutInflater) {
        val contexto = contenedor.context
        contenedor.removeAllViews()
        ESTADOS_LEYENDA.forEachIndexed { indice, estado ->
            val item = ItemLeyendaBinding.inflate(inflater, contenedor, false)
            val color = ContextCompat.getColor(contexto, estado.colorRes())
            item.iconoEstado.setImageResource(estado.iconoRes())
            item.iconoEstado.imageTintList = ColorStateList.valueOf(color)
            item.textoLeyenda.text = contexto.getString(estado.etiquetaRes())
            val lp = item.root.layoutParams as? LinearLayout.LayoutParams
            if (lp != null && indice == ESTADOS_LEYENDA.lastIndex) {
                lp.marginEnd = 0
                item.root.layoutParams = lp
            }
            contenedor.addView(item.root)
        }
    }

    private fun configurarChip(
        binding: ItemChipSalonBinding,
        filtroEstado: EstadoMesa?,
        total: Int,
        seleccionado: Boolean,
        esTablet: Boolean,
        densidad: Float,
    ) {
        val contexto = binding.root.context
        binding.root.isSelected = seleccionado
        binding.contadorChip.text = total.toString()

        if (filtroEstado == null) {
            binding.iconoChip.isVisible = false
            binding.textoChip.isVisible = true
            binding.textoChip.text = contexto.getString(R.string.zona_todas)
            binding.root.contentDescription = contexto.getString(R.string.zona_todas)
        } else {
            val etiqueta = contexto.getString(filtroEstado.etiquetaRes())
            val mostrarTexto = esTablet
            binding.iconoChip.isVisible = true
            binding.iconoChip.setImageResource(filtroEstado.iconoRes())
            val colorIcono = ContextCompat.getColor(
                contexto,
                if (seleccionado) R.color.white else filtroEstado.colorRes(),
            )
            binding.iconoChip.imageTintList = ColorStateList.valueOf(colorIcono)
            binding.textoChip.isVisible = mostrarTexto
            if (mostrarTexto) {
                binding.textoChip.text = contexto.getString(filtroEstado.etiquetaChipTabletRes())
            }
            binding.root.contentDescription = etiqueta
        }

        val paddingHorizontalDp = when {
            esTablet -> 14f
            filtroEstado == null -> 9f
            else -> 4f
        }
        val paddingHorizontalPx = (paddingHorizontalDp * densidad).roundToInt()
        binding.root.setPadding(paddingHorizontalPx, 0, paddingHorizontalPx, 0)
    }

    private fun crearLayoutParamsChip(
        esTodas: Boolean,
        esTablet: Boolean,
        altoPx: Int,
        margenFinPx: Int,
    ): LinearLayout.LayoutParams {
        val params = if (esTablet) {
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, altoPx)
        } else {
            val peso = if (esTodas) 1.55f else 1f
            LinearLayout.LayoutParams(0, altoPx, peso)
        }
        params.marginEnd = margenFinPx
        return params
    }
}
