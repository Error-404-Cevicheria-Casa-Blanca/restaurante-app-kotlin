package com.lacasablanca.mesero.ui.mesas

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lacasablanca.mesero.databinding.ItemMesaBinding
import com.lacasablanca.mesero.domain.model.Mesa

/**
 * Cuadricula de mesas. Solo pinta; los eventos los reenvia al Fragment.
 * PRB-93: [alAccionar] abre el menu de dividir/fusionar/transferir desde el
 * boton de la tarjeta o con un long press.
 */
class MesaAdapter(
    private val alTocar: (Mesa) -> Unit,
    private val alAccionar: (Mesa) -> Unit
) : ListAdapter<Mesa, MesaAdapter.MesaViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MesaViewHolder {
        val binding = ItemMesaBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MesaViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MesaViewHolder, position: Int) {
        holder.pintar(getItem(position), alTocar, alAccionar)
    }

    class MesaViewHolder(private val binding: ItemMesaBinding) : RecyclerView.ViewHolder(binding.root) {

        fun pintar(mesa: Mesa, alTocar: (Mesa) -> Unit, alAccionar: (Mesa) -> Unit) {
            MesaTarjetaMarea.vincular(
                binding = binding,
                mesa = mesa,
                alTocar = alTocar,
                alAccionar = alAccionar,
            )
        }
    }
}

private val DIFF = object : DiffUtil.ItemCallback<Mesa>() {
    override fun areItemsTheSame(anterior: Mesa, actual: Mesa): Boolean = anterior.id == actual.id
    override fun areContentsTheSame(anterior: Mesa, actual: Mesa): Boolean = anterior == actual
}
