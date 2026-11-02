package com.lacasablanca.mesero.ui.comanda

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.lacasablanca.mesero.R
import com.lacasablanca.mesero.databinding.ItemCatalogoEncabezadoBinding
import com.lacasablanca.mesero.databinding.ItemCatalogoProductoBinding
import com.lacasablanca.mesero.domain.model.Producto
import com.lacasablanca.mesero.ui.common.formatearMoneda

/**
 * Catalogo agrupado por categorias. Solo pinta; el boton "Agregar" reenvia
 * el producto a la Activity (que delega en CarritoViewModel).
 */
class CatalogoAdapter(
    private val alAgregar: (Producto) -> Unit
) : ListAdapter<FilaCatalogo, RecyclerView.ViewHolder>(DIFF) {

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is FilaCatalogo.Encabezado -> TIPO_ENCABEZADO
        is FilaCatalogo.Fila -> TIPO_PRODUCTO
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TIPO_ENCABEZADO -> EncabezadoViewHolder(
                ItemCatalogoEncabezadoBinding.inflate(inflater, parent, false)
            )

            else -> ProductoViewHolder(
                ItemCatalogoProductoBinding.inflate(inflater, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val fila = getItem(position)) {
            is FilaCatalogo.Encabezado -> (holder as EncabezadoViewHolder).pintar(fila)
            is FilaCatalogo.Fila -> (holder as ProductoViewHolder).pintar(fila.producto, alAgregar)
        }
    }

    class EncabezadoViewHolder(
        private val binding: ItemCatalogoEncabezadoBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun pintar(fila: FilaCatalogo.Encabezado) {
            binding.textoEncabezadoCategoria.text =
                fila.nombre ?: binding.root.context.getString(R.string.comanda_categoria_otros)
        }
    }

    class ProductoViewHolder(
        private val binding: ItemCatalogoProductoBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun pintar(producto: Producto, alAgregar: (Producto) -> Unit) {
            val contexto = binding.root.context

            binding.textoNombreProducto.text = producto.nombre
            binding.textoDescripcionProducto.text = producto.descripcion
            binding.textoPrecioProducto.text = formatearMoneda(producto.precio)

            binding.textoNoDisponible.isVisible = !producto.disponible
            binding.botonAgregar.isEnabled = producto.disponible
            binding.botonAgregar.contentDescription =
                contexto.getString(R.string.cd_agregar_producto, producto.nombre)
            binding.botonAgregar.setOnClickListener {
                if (producto.disponible) alAgregar(producto)
            }

            cargarImagen(producto)
        }

        private fun cargarImagen(producto: Producto) {
            val contexto = binding.root.context
            val respaldo = when {
                producto.nombre.contains("ceviche", ignoreCase = true) -> R.drawable.img_ceviche
                producto.nombre.contains("tiradito", ignoreCase = true) -> R.drawable.img_tiradito
                else -> R.drawable.ic_restaurant
            }

            binding.imagenProducto.contentDescription =
                contexto.getString(R.string.cd_imagen_producto, producto.nombre)

            if (!producto.imagenUrl.isNullOrBlank()) {
                binding.imagenProducto.scaleType = ImageView.ScaleType.CENTER_CROP
                binding.imagenProducto.setPadding(0, 0, 0, 0)
                Glide.with(binding.imagenProducto)
                    .load(producto.imagenUrl)
                    .placeholder(respaldo)
                    .error(respaldo)
                    .centerCrop()
                    .into(binding.imagenProducto)
            } else {
                val esFoto = respaldo != R.drawable.ic_restaurant
                binding.imagenProducto.scaleType = if (esFoto) {
                    ImageView.ScaleType.CENTER_CROP
                } else {
                    ImageView.ScaleType.CENTER_INSIDE
                }
                val padding = if (esFoto) 0 else {
                    binding.root.resources.getDimensionPixelSize(R.dimen.espacio_m)
                }
                binding.imagenProducto.setPadding(padding, padding, padding, padding)
                binding.imagenProducto.setImageResource(respaldo)
            }
        }
    }

    private companion object {
        const val TIPO_ENCABEZADO = 0
        const val TIPO_PRODUCTO = 1
    }
}

private val DIFF = object : DiffUtil.ItemCallback<FilaCatalogo>() {
    override fun areItemsTheSame(anterior: FilaCatalogo, actual: FilaCatalogo): Boolean {
        return when {
            anterior is FilaCatalogo.Encabezado && actual is FilaCatalogo.Encabezado ->
                anterior.categoriaId == actual.categoriaId

            anterior is FilaCatalogo.Fila && actual is FilaCatalogo.Fila ->
                anterior.producto.id == actual.producto.id

            else -> false
        }
    }

    override fun areContentsTheSame(anterior: FilaCatalogo, actual: FilaCatalogo): Boolean =
        anterior == actual
}
