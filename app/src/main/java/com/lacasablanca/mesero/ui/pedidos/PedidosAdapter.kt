package com.lacasablanca.mesero.ui.pedidos

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lacasablanca.mesero.R
import com.lacasablanca.mesero.databinding.ItemPedidoBinding
import com.lacasablanca.mesero.domain.model.OrigenPedido
import com.lacasablanca.mesero.domain.model.PedidoActivo
import com.lacasablanca.mesero.ui.common.formatearMoneda

/**
 * Lista de pedidos activos (PRB-88, Fase 6).
 * La tarjeta solo muestra Cobrar cuando el pedido no tiene mesa.
 */
class PedidosAdapter(
    private val alCobrar: (PedidoActivo) -> Unit
) : ListAdapter<PedidoActivo, PedidosAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPedidoBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemPedidoBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(pedido: PedidoActivo) {
            val context = binding.root.context
            binding.textoPedidoNumero.text = context.getString(
                R.string.pedidos_numero,
                pedido.id
            )
            binding.textoTipoPedido.text = origen(pedido)
            binding.textoEstadoPedido.text = context.getString(
                estadoRes(pedido.estado)
            )
            binding.textoTotalPedido.text = context.getString(
                R.string.pedidos_total,
                formatearMoneda(pedido.total)
            )
            binding.btnCobrarPedido.isVisible = pedido.puedeCobrar
            binding.btnCobrarPedido.contentDescription = context.getString(
                R.string.cd_cobrar_pedido,
                pedido.id
            )
            binding.btnCobrarPedido.setOnClickListener {
                if (pedido.puedeCobrar) alCobrar(pedido)
            }
        }

        private fun origen(pedido: PedidoActivo): String = when (pedido.tipoPedido) {
            OrigenPedido.MESA -> pedido.mesaId?.let {
                binding.root.context.getString(R.string.pedidos_origen_mesa, it)
            } ?: binding.root.context.getString(R.string.pedidos_origen_mesa_sin_numero)

            OrigenPedido.PARA_LLEVAR -> pedido.clienteNombre?.let {
                binding.root.context.getString(R.string.pedidos_origen_para_llevar, it)
            } ?: binding.root.context.getString(R.string.pedidos_origen_para_llevar_sin_cliente)

            OrigenPedido.DELIVERY -> pedido.clienteNombre?.let {
                binding.root.context.getString(R.string.pedidos_origen_delivery, it)
            } ?: binding.root.context.getString(R.string.pedidos_origen_delivery_sin_cliente)
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<PedidoActivo>() {
            override fun areItemsTheSame(
                oldItem: PedidoActivo,
                newItem: PedidoActivo
            ): Boolean = oldItem.id == newItem.id

            override fun areContentsTheSame(
                oldItem: PedidoActivo,
                newItem: PedidoActivo
            ): Boolean = oldItem == newItem
        }

        fun estadoRes(estado: String): Int = when (estado.trim().uppercase()) {
            "PENDIENTE" -> R.string.pedidos_estado_pendiente
            "EN_PREPARACION" -> R.string.pedidos_estado_en_preparacion
            "LISTO" -> R.string.pedidos_estado_listo
            "SERVIDO" -> R.string.pedidos_estado_servido
            else -> R.string.pedidos_estado_desconocido
        }
    }
}
