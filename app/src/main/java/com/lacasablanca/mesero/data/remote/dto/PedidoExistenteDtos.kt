package com.lacasablanca.mesero.data.remote.dto

import com.google.gson.annotations.SerializedName
import com.lacasablanca.mesero.domain.model.EstadoPlato
import com.lacasablanca.mesero.domain.model.ItemCarrito
import com.lacasablanca.mesero.domain.model.Modificador
import com.lacasablanca.mesero.domain.model.PedidoExistente
import com.lacasablanca.mesero.domain.model.Producto
import java.math.BigDecimal
import java.math.RoundingMode

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
    val subtotal: BigDecimal? = null,
    val notas: String? = null,
    /**
     * PRB-76: PENDIENTE, EN_PREPARACION, LISTO, SERVIDO o ANULADO. Se deja
     * como texto para que un valor nuevo del backend no rompa el parseo.
     * PRB-80 (robustez): el backend Java tambien puede nombrarlo `estado_item`.
     */
    @SerializedName(value = "estado", alternate = ["estado_item"])
    val estado: String? = null,
    /**
     * PRB-92: modificadores elegidos que devuelve `DetallePedidoResponse`
     * (`nombre`, `precio_adicional`). Si el backend aun no los envia, la lista
     * queda vacia y la linea se muestra igual que antes.
     */
    val modificadores: List<ModificadorDto>? = null
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
    val modificadoresDominio = modificadores.orEmpty().mapNotNull { it.aDominio() }

    // El backend devuelve `precio_unitario` YA con el recargo de modificadores
    // (PedidoService: precio + recargo). Para que ItemCarrito no sume dos veces
    // el recargo, se reconstruye el precio base restandolo. Si el backend
    // enviara el base, el recargo seria cero y el resultado no cambia.
    val precioUnitarioFinal = precioUnitario ?: BigDecimal.ZERO
    val precioBase = (precioUnitarioFinal - Modificador.recargoTotal(modificadoresDominio))
        .max(BigDecimal.ZERO)
        .setScale(2, RoundingMode.HALF_UP)

    return ItemCarrito(
        producto = Producto(
            id = productoId,
            nombre = nombre.orEmpty(),
            descripcion = "",
            precio = precioBase,
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
            ?.let(EstadoPlato::desdeApi),
        modificadores = modificadoresDominio
    )
}
