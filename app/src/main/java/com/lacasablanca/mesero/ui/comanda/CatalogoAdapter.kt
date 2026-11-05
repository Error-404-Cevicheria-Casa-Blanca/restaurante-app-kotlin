package com.lacasablanca.mesero.ui.comanda

import android.view.HapticFeedbackConstants
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
 * Catalogo agrupado por categorias. Solo pinta; los botones reenvian el
 * producto a la Activity (que delega en [CarritoViewModel]).
 *
 * [actualizarCantidades] recibe el mapa producto -> cantidad del carrito:
 * si la cantidad es mayor que cero, la tarjeta muestra "[-] N [+]" en lugar
 * del boton "+", con una animacion corta de confirmacion.
 */
class CatalogoAdapter(
    private val alAgregar: (Producto) -> Unit,
    private val alAumentar: (Long) -> Unit,
    private val alDisminuir: (Long) -> Unit
) : ListAdapter<FilaCatalogo, RecyclerView.ViewHolder>(DIFF) {

    private var cantidades: Map<Long, Int> = emptyMap()

    /**
     * Actualiza las cantidades visibles sin reconstruir el catalogo. Solo
     * notifica las filas de producto cuya cantidad cambio.
     */
    fun actualizarCantidades(nuevas: Map<Long, Int>) {
        if (nuevas == cantidades) return

        val anteriores = cantidades
        cantidades = nuevas
        currentList.forEachIndexed { indice, fila ->
            if (fila is FilaCatalogo.Fila && anteriores[fila.producto.id] != nuevas[fila.producto.id]) {
                notifyItemChanged(indice)
            }
        }
    }

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
            is FilaCatalogo.Fila -> (holder as ProductoViewHolder).pintar(
                producto = fila.producto,
                cantidad = cantidades[fila.producto.id] ?: 0,
                alAgregar = alAgregar,
                alAumentar = alAumentar,
                alDisminuir = alDisminuir
            )
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

        private var productoIdMostrado = -1L
        private var cantidadMostrada = -1

        fun pintar(
            producto: Producto,
            cantidad: Int,
            alAgregar: (Producto) -> Unit,
            alAumentar: (Long) -> Unit,
            alDisminuir: (Long) -> Unit
        ) {
            val contexto = binding.root.context
            val mostrarStepper = producto.disponible && cantidad > 0

            binding.textoNombreProducto.text = producto.nombre
            binding.textoDescripcionProducto.text = producto.descripcion
            binding.textoPrecioProducto.text = formatearMoneda(producto.precio)
            binding.textoNoDisponible.isVisible = !producto.disponible

            binding.botonAgregar.isEnabled = producto.disponible
            binding.botonAgregar.alpha = if (producto.disponible) 1f else ALPHA_NO_DISPONIBLE
            binding.botonAgregar.contentDescription =
                contexto.getString(R.string.cd_agregar_producto, producto.nombre)
            binding.botonAgregar.setOnClickListener { vista ->
                if (producto.disponible) {
                    vista.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    alAgregar(producto)
                }
            }

            binding.textoCantidadPlato.text = cantidad.toString()
            binding.textoCantidadPlato.contentDescription =
                contexto.getString(R.string.cd_cantidad_plato, cantidad)

            binding.btnAumentarPlato.contentDescription =
                contexto.getString(R.string.cd_aumentar_producto, producto.nombre)
            binding.btnDisminuirPlato.contentDescription =
                contexto.getString(R.string.cd_disminuir_producto, producto.nombre)
            binding.btnAumentarPlato.setOnClickListener { vista ->
                vista.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                alAumentar(producto.id)
            }
            binding.btnDisminuirPlato.setOnClickListener { alDisminuir(producto.id) }

            aplicarModo(
                productoId = producto.id,
                cantidad = cantidad,
                mostrarStepper = mostrarStepper
            )
            cargarImagen(producto)
        }

        /**
         * Alterna entre el boton "+" y el stepper. La animacion solo corre
         * cuando cambia la cantidad del mismo producto; al reciclar la vista
         * se aplica el estado final sin parpadeos.
         */
        private fun aplicarModo(productoId: Long, cantidad: Int, mostrarStepper: Boolean) {
            val cambioDeProducto = productoIdMostrado != productoId
            productoIdMostrado = productoId

            binding.botonAgregar.isVisible = !mostrarStepper
            binding.grupoCantidad.isVisible = mostrarStepper

            if (!cambioDeProducto && cantidadMostrada >= 0 && cantidadMostrada != cantidad) {
                val objetivo = if (mostrarStepper) binding.grupoCantidad else binding.botonAgregar
                objetivo.animate().cancel()
                objetivo.alpha = 0f
                objetivo.scaleX = ESCALA_INICIAL
                objetivo.scaleY = ESCALA_INICIAL
                objetivo.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(DURACION_FEEDBACK_MS)
                    .start()
            }

            cantidadMostrada = cantidad
        }

        private fun cargarImagen(producto: Producto) {
            val contexto = binding.root.context
            val respaldo = when {
                producto.nombre.contains("ceviche", ignoreCase = true) -> R.drawable.img_ceviche
                producto.nombre.contains("tiradito", ignoreCase = true) -> R.drawable.img_tiradito
                else -> R.drawable.ic_restaurant_placeholder
            }
            val esFoto = respaldo != R.drawable.ic_restaurant_placeholder

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
                binding.imagenProducto.scaleType = if (esFoto) {
                    ImageView.ScaleType.CENTER_CROP
                } else {
                    ImageView.ScaleType.CENTER_INSIDE
                }
                val padding = if (esFoto) 0 else {
                    binding.root.resources.getDimensionPixelSize(R.dimen.espacio_l)
                }
                binding.imagenProducto.setPadding(padding, padding, padding, padding)
                binding.imagenProducto.setImageResource(respaldo)
            }
        }
    }

    private companion object {
        const val TIPO_ENCABEZADO = 0
        const val TIPO_PRODUCTO = 1
        const val DURACION_FEEDBACK_MS = 140L
        const val ESCALA_INICIAL = 0.88f
        const val ALPHA_NO_DISPONIBLE = 0.55f
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
