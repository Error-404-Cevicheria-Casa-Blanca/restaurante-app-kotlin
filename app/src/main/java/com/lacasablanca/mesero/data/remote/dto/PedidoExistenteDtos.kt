package com.lacasablanca.mesero.data.remote.dto

import com.google.gson.annotations.SerializedName
import com.lacasablanca.mesero.domain.model.EstadoPlato
import com.lacasablanca.mesero.domain.model.ItemCarrito
import com.lacasablanca.mesero.domain.model.PedidoExistente
import com.lacasablanca.mesero.domain.model.Producto
import java.math.BigDecimal

/**
 * GET /api/v1/pedidos?mesa_id=X (PRB-83, depende de PRB-82).
 *
 * El contrato v1 todavia no fija la forma de esta respuesta: se asume el
 * pedido activo de la mesa con una lista `items` y los datos del plato
 * (`producto_id`, `nombre`, `precio_unitario`, `cantidad`, `notas`).
 * Los `alternate` toleran los nombres que suele usar el backend Java para el
 * detalle consolidado. Si cambia, solo se ajusta este DTO.
 */
data class PedidoExistenteDto(
    val id: Long? = null,
    @SerializedName("mesa_id") val mesaId: Long? = null,
    val estado: String? = null,
    @SerializedName(value = "items", alternate = ["detalle", "detalles"])
    val items: List<ItemPedidoExistenteDto>? = null
)

data class ItemPedidoExistenteDto(
    /**
     * Id del detalle (PRB-77). El backend Java lo devuelve como `id`; los
     * `alternate` toleran `item_id`/`detalle_id` mientras se confirma el
     * contrato. Sin este id no se puede llamar al endpoint de anulacion.
     */
    @SerializedName(value = "id", alternate = ["item_id", "detalle_id"])
    val id: Long? = null,
    @SerializedName("producto_id") val productoId: Long? = null,
    @SerializedName(value = "nombre", alternate = ["producto_nombre"])
    val nombre: String? = null,
    val cantidad: Int? = null,
    @SerializedName(value = "precio_unitario", alternate = ["precio"])
    val precioUnitario: BigDecimal? = null,
    val notas: String? = null,
    /**
     * PRB-76: PENDIENTE, EN_PREPARACION, LISTO, SERVIDO o ANULADO. Se deja
     * como texto para que un valor nuevo del backend no rompa el parseo.
     * PRB-80 (robustez): el backend Java tambien puede nombrarlo `estado_item`.
     */
    @SerializedName(value = "estado", alternate = ["estado_item"])
    val estado: String? = null
)

/**
 * Mapeo a dominio. Un item sin cantidad valida se descarta para no mostrar
 * lineas vacias; el nombre puede venir vacio y la UI lo rotula con su id.
 */
fun PedidoExistenteDto.aDominio(): PedidoExistente = PedidoExistente(
    pedidoId = id ?: 0L,
    estado = estado.orEmpty(),
    lineas = items.orEmpty()
        .mapNotNull { item -> item.aItemCarrito() }
)

private fun ItemPedidoExistenteDto.aItemCarrito(): ItemCarrito? {
    val cantidadValida = (cantidad ?: 0).takeIf { it > 0 } ?: return null
    val productoId = productoId ?: return null
    return ItemCarrito(
        producto = Producto(
            id = productoId,
            nombre = nombre.orEmpty(),
            descripcion = "",
            precio = precioUnitario ?: BigDecimal.ZERO,
            disponible = true,
            categoriaId = 0L,
            imagenUrl = null,
            estacion = null
        ),
        cantidad = cantidadValida,
        notas = notas.orEmpty(),
        itemId = id,
        // Sin estado en el JSON no se inventa uno: la UI simplemente no
        // muestra la pildora (PRB-76).
        estadoPlato = estado
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let(EstadoPlato::desdeApi)
    )
}
