package com.lacasablanca.mesero.ui.comanda

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lacasablanca.mesero.R
import com.lacasablanca.mesero.databinding.ItemCarritoBinding
import com.lacasablanca.mesero.domain.model.ItemCarrito
import com.lacasablanca.mesero.ui.common.formatearMoneda

/**
 * Carrito local. Solo pinta; +/-/eliminar se reenvian a la Activity.
 *
 * Las lineas con `editable = false` son platos ya pedidos (PRB-83): muestran
 * la etiqueta "Ya pedido" y no ofrecen controles de cantidad ni de borrado.
 */
class CarritoAdapter(
    private val alAumentar: (Long) -> Unit,
    private val alDisminuir: (Long) -> Unit,
    private val alEliminar: (Long) -> Unit,
    private val alEditarNotas: (ItemCarrito) -> Unit,
    private val alAnular: (ItemCarrito) -> Unit
) : ListAdapter<LineaCarrito, CarritoAdapter.CarritoViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CarritoViewHolder {
        val binding = ItemCarritoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CarritoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CarritoViewHolder, position: Int) {
        holder.pintar(
            getItem(position),
            alAumentar,
            alDisminuir,
            alEliminar,
            alEditarNotas,
            alAnular
        )
    }

    class CarritoViewHolder(
        private val binding: ItemCarritoBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun pintar(
            linea: LineaCarrito,
            alAumentar: (Long) -> Unit,
            alDisminuir: (Long) -> Unit,
            alEliminar: (Long) -> Unit,
            alEditarNotas: (ItemCarrito) -> Unit,
            alAnular: (ItemCarrito) -> Unit
        ) {
            val contexto = binding.root.context
            val item = linea.item
            val producto = item.producto
            val editable = linea.editable

            binding.textoNombreCarrito.text = producto.nombre.ifBlank {
                contexto.getString(R.string.comanda_plato_sin_nombre, producto.id)
            }
            binding.textoPrecioCarrito.text = contexto.getString(
                R.string.comanda_carrito_precio,
                formatearMoneda(producto.precio),
                formatearMoneda(item.subtotal)
            )

            binding.textoCantidad.text = item.cantidad.toString()
            binding.textoCantidad.contentDescription =
                contexto.getString(R.string.comanda_cantidad_cd, item.cantidad)

            binding.textoYaPedido.isVisible = !editable
            binding.btnNota.isVisible = editable
            binding.filaControles.isVisible = editable
            // PRB-77: solo un plato ya enviado y con id de detalle se anula.
            binding.btnAnular.isVisible = !editable && item.itemId != null

            binding.btnAumentar.contentDescription =
                contexto.getString(R.string.cd_aumentar_producto, producto.nombre)
            binding.btnDisminuir.contentDescription =
                contexto.getString(R.string.cd_disminuir_producto, producto.nombre)
            binding.btnEliminar.contentDescription =
                contexto.getString(R.string.cd_eliminar_producto, producto.nombre)
            binding.btnNota.contentDescription =
                contexto.getString(R.string.cd_nota_producto, producto.nombre)
            binding.btnAnular.contentDescription =
                contexto.getString(R.string.cd_anular_producto, producto.nombre)

            binding.textoNotaCarrito.isVisible = item.notas.isNotBlank()
            binding.textoNotaCarrito.text = contexto.getString(
                R.string.comanda_nota_etiqueta,
                item.notas
            )

            if (editable) {
                binding.btnAumentar.setOnClickListener { alAumentar(producto.id) }
                binding.btnDisminuir.setOnClickListener { alDisminuir(producto.id) }
                binding.btnEliminar.setOnClickListener { alEliminar(producto.id) }
                binding.btnNota.setOnClickListener { alEditarNotas(item) }
            } else {
                binding.btnAnular.setOnClickListener { alAnular(item) }
            }
        }
    }
}

private val DIFF = object : DiffUtil.ItemCallback<LineaCarrito>() {
    override fun areItemsTheSame(anterior: LineaCarrito, actual: LineaCarrito): Boolean {
        // Las lineas ya pedidas se identifican por su id de detalle: puede
        // haber dos lineas del mismo plato con notas o cantidades distintas.
        val idAnterior = anterior.item.itemId
        val idActual = actual.item.itemId
        if (idAnterior != null || idActual != null) return idAnterior == idActual

        return anterior.item.producto.id == actual.item.producto.id &&
            anterior.editable == actual.editable
    }

    override fun areContentsTheSame(anterior: LineaCarrito, actual: LineaCarrito): Boolean =
        anterior == actual
}
