package com.lacasablanca.mesero.ui.cobro

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lacasablanca.mesero.R
import com.lacasablanca.mesero.databinding.ItemCuotaBinding
import com.lacasablanca.mesero.ui.common.formatearMoneda

class CuotaAdapter : ListAdapter<CuotaComensal, CuotaAdapter.CuotaViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CuotaViewHolder {
        val binding = ItemCuotaBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CuotaViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CuotaViewHolder, position: Int) {
        holder.pintar(getItem(position))
    }

    class CuotaViewHolder(
        private val binding: ItemCuotaBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun pintar(cuota: CuotaComensal) {
            val contexto = binding.root.context
            binding.textoComensalCuota.text = contexto.getString(
                R.string.cobro_comensal,
                cuota.numero
            )
            binding.textoMontoCuota.text = formatearMoneda(cuota.monto)
        }
    }
}

private val DIFF = object : DiffUtil.ItemCallback<CuotaComensal>() {
    override fun areItemsTheSame(anterior: CuotaComensal, actual: CuotaComensal): Boolean =
        anterior.numero == actual.numero

    override fun areContentsTheSame(anterior: CuotaComensal, actual: CuotaComensal): Boolean =
        anterior == actual
}
