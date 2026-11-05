package com.lacasablanca.mesero.ui.cobro

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lacasablanca.mesero.R
import com.lacasablanca.mesero.data.remote.dto.PagoRequest
import com.lacasablanca.mesero.databinding.ItemPagoBinding
import com.lacasablanca.mesero.domain.model.MetodoPago
import com.lacasablanca.mesero.ui.common.formatearMoneda

/** Pagos agregados. El boton de eliminar se reenvia a la Activity. */
class PagoAdapter(
    private val alEliminar: (Int) -> Unit
) : ListAdapter<PagoRequest, PagoAdapter.PagoViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PagoViewHolder {
        val binding = ItemPagoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PagoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PagoViewHolder, position: Int) {
        holder.pintar(getItem(position), position, alEliminar)
    }

    class PagoViewHolder(
        private val binding: ItemPagoBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun pintar(pago: PagoRequest, posicion: Int, alEliminar: (Int) -> Unit) {
            val contexto = binding.root.context
            binding.textoMetodoPago.text = contexto.getString(etiquetaMetodo(pago.metodo))
            binding.textoMontoPago.text = formatearMoneda(pago.monto)
            binding.btnEliminarPago.contentDescription = contexto.getString(
                R.string.cd_eliminar_pago,
                binding.textoMetodoPago.text?.toString().orEmpty()
            )
            binding.btnEliminarPago.setOnClickListener { alEliminar(posicion) }
        }
    }
}

@StringRes
private fun etiquetaMetodo(metodo: String): Int = when (metodo) {
    MetodoPago.EFECTIVO.valorApi -> R.string.cobro_efectivo
    MetodoPago.YAPE.valorApi -> R.string.cobro_yape
    MetodoPago.PLIN.valorApi -> R.string.cobro_plin
    MetodoPago.TARJETA.valorApi -> R.string.cobro_tarjeta
    else -> R.string.cobro_metodo_otro
}

private val DIFF = object : DiffUtil.ItemCallback<PagoRequest>() {
    override fun areItemsTheSame(anterior: PagoRequest, actual: PagoRequest): Boolean =
        anterior === actual

    override fun areContentsTheSame(anterior: PagoRequest, actual: PagoRequest): Boolean =
        anterior == actual
}
