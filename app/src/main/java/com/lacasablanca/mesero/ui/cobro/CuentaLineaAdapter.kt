package com.lacasablanca.mesero.ui.cobro

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lacasablanca.mesero.R
import com.lacasablanca.mesero.data.remote.dto.ItemCuenta
import com.lacasablanca.mesero.databinding.ItemCuentaLineaBinding
import com.lacasablanca.mesero.ui.common.formatearMoneda
import java.math.BigDecimal

/** Lineas del resumen de cuenta. Solo pinta. */
class CuentaLineaAdapter : ListAdapter<ItemCuenta, CuentaLineaAdapter.CuentaViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CuentaViewHolder {
        val binding = ItemCuentaLineaBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CuentaViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CuentaViewHolder, position: Int) {
        holder.pintar(getItem(position))
    }

    class CuentaViewHolder(
        private val binding: ItemCuentaLineaBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun pintar(item: ItemCuenta) {
            val contexto = binding.root.context
            binding.textoNombreLinea.text = item.nombre.orEmpty()
            binding.textoCantidadLinea.text = contexto.getString(
                R.string.cobro_linea_cantidad,
                item.cantidad ?: 0
            )
            binding.textoPrecioLinea.text = contexto.getString(
                R.string.cobro_linea_precio,
                formatearMoneda(item.precioUnitario ?: BigDecimal.ZERO)
            )
            binding.textoSubtotalLinea.text = formatearMoneda(
                item.subtotalLinea ?: BigDecimal.ZERO
            )
        }
    }
}

private val DIFF = object : DiffUtil.ItemCallback<ItemCuenta>() {
    override fun areItemsTheSame(anterior: ItemCuenta, actual: ItemCuenta): Boolean =
        anterior === actual

    override fun areContentsTheSame(anterior: ItemCuenta, actual: ItemCuenta): Boolean =
        anterior == actual
}
