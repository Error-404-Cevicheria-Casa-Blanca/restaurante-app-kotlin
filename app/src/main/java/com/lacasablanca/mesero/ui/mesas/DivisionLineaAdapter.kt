package com.lacasablanca.mesero.ui.mesas

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lacasablanca.mesero.R
import com.lacasablanca.mesero.databinding.ItemDivisionLineaBinding
import com.lacasablanca.mesero.domain.model.ItemCarrito
import com.lacasablanca.mesero.ui.common.formatearMoneda

/** Linea enviada que puede pasar a la cuenta nueva (PRB-93). */
internal data class DivisionLineaItem(
    val item: ItemCarrito,
    val seleccionada: Boolean
) {
    val itemId: Long get() = item.itemId ?: 0L
}

internal class DivisionLineaAdapter(
    private val alCambiarSeleccion: (DivisionLineaItem) -> Unit
) : ListAdapter<DivisionLineaItem, DivisionLineaAdapter.LineaViewHolder>(DIFF) {

    /** Ids del detalle que el backend acepta en `item_ids`. */
    fun seleccionados(): List<Long> =
        currentList.filter { it.seleccionada }.map { it.itemId }.filter { it > 0L }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LineaViewHolder {
        val binding = ItemDivisionLineaBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return LineaViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LineaViewHolder, position: Int) {
        holder.pintar(getItem(position), alCambiarSeleccion)
    }

    class LineaViewHolder(
        private val binding: ItemDivisionLineaBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun pintar(
            linea: DivisionLineaItem,
            alCambiarSeleccion: (DivisionLineaItem) -> Unit
        ) {
            val contexto = binding.root.context
            val nombre = linea.item.producto.nombre.ifBlank {
                linea.item.producto.descripcion.ifBlank {
                    contexto.getString(R.string.plato_sin_nombre)
                }
            }
            val subtotal = formatearMoneda(linea.item.subtotal)

            binding.checkPlatoDivision.isChecked = linea.seleccionada
            binding.textoPlatoDivision.text = nombre
            binding.textoCantidadDivision.text = contexto.getString(
                R.string.division_item_cantidad,
                linea.item.cantidad
            )
            binding.textoSubtotalDivision.text = subtotal
            binding.root.isSelected = linea.seleccionada
            binding.root.contentDescription = contexto.getString(
                R.string.cd_division_plato,
                nombre,
                linea.item.cantidad,
                subtotal
            )
            binding.root.setOnClickListener {
                alCambiarSeleccion(linea.copy(seleccionada = !linea.seleccionada))
            }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<DivisionLineaItem>() {
            override fun areItemsTheSame(
                anterior: DivisionLineaItem,
                actual: DivisionLineaItem
            ): Boolean = anterior.itemId == actual.itemId

            override fun areContentsTheSame(
                anterior: DivisionLineaItem,
                actual: DivisionLineaItem
            ): Boolean = anterior == actual
        }
    }
}
