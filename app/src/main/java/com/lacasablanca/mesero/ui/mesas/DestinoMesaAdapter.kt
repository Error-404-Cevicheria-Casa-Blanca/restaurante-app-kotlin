package com.lacasablanca.mesero.ui.mesas

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lacasablanca.mesero.R
import com.lacasablanca.mesero.databinding.ItemMesaDestinoBinding
import com.lacasablanca.mesero.domain.model.Mesa

/**
 * Fila del dialogo de mesa destino (PRB-93). La etiqueta la decide el dialogo:
 * el estado de la mesa o "Misma mesa (cuenta separada)" en la division.
 */
internal data class DestinoMesaItem(
    val mesa: Mesa,
    val etiqueta: String,
    val seleccionada: Boolean
)

internal class DestinoMesaAdapter(
    private val alSeleccionar: (DestinoMesaItem) -> Unit
) : ListAdapter<DestinoMesaItem, DestinoMesaAdapter.DestinoViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DestinoViewHolder {
        val binding = ItemMesaDestinoBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return DestinoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DestinoViewHolder, position: Int) {
        holder.pintar(getItem(position), alSeleccionar)
    }

    class DestinoViewHolder(
        private val binding: ItemMesaDestinoBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun pintar(item: DestinoMesaItem, alSeleccionar: (DestinoMesaItem) -> Unit) {
            val contexto = binding.root.context
            binding.radioDestino.isChecked = item.seleccionada
            binding.textoDestinoMesa.text =
                contexto.getString(R.string.mesa_titulo, item.mesa.numero)
            binding.textoEstadoDestino.text = item.etiqueta
            binding.root.contentDescription = contexto.getString(
                R.string.cd_mesa_destino,
                item.mesa.numero,
                item.etiqueta
            )
            binding.root.isSelected = item.seleccionada
            binding.root.setOnClickListener { alSeleccionar(item) }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<DestinoMesaItem>() {
            override fun areItemsTheSame(anterior: DestinoMesaItem, actual: DestinoMesaItem): Boolean =
                anterior.mesa.id == actual.mesa.id

            override fun areContentsTheSame(
                anterior: DestinoMesaItem,
                actual: DestinoMesaItem
            ): Boolean = anterior == actual
        }
    }
}
