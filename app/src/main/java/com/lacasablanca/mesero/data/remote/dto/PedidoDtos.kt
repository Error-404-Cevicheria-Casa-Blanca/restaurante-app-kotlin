package com.lacasablanca.mesero.data.remote.dto

import com.google.gson.annotations.SerializedName
import com.lacasablanca.mesero.domain.model.PedidoEnviado
import java.math.BigDecimal

/**
 * POST /api/v1/pedidos. Nombres EXACTOS del backend Java (ItemPedidoRequest.java):
 * `producto_id`, `cantidad`, `notas`, `modificadores`.
 *
 * PRB-92: cada modificador viaja como `{nombre, precio_adicional}`. El backend
 * valida que el nombre no este en blanco y que el precio no sea negativo, y
 * recalcula el total con su propia carta.
 */
data class ItemPedidoRequest(
    @SerializedName("producto_id") val productoId: Long,
    val cantidad: Int,
    val notas: String? = null,
    val modificadores: List<ModificadorRequest> = emptyList()
)

/**
 * Modificador de una linea del pedido (PRB-92). Mismos nombres que
 * `ModificadorItemDto.java`: `nombre` y `precio_adicional`.
 */
data class ModificadorRequest(
    val nombre: String,
    @SerializedName("precio_adicional") val precioAdicional: BigDecimal = BigDecimal.ZERO
)

/**
 * POST /api/v1/pedidos (CrearPedidoRequest.java): `mesa_id` + lista `items`.
 *
 * [clientRequestId] es la clave de idempotencia del pedido. Se envia tambien
 * en el header `Idempotency-Key`; el backend la usa para no duplicar el
 * pedido si el mesero reintenta tras un fallo de red (PRB-2).
 */
data class CrearPedidoRequest(
    @SerializedName("mesa_id") val mesaId: Long,
    val items: List<ItemPedidoRequest>,
    @SerializedName("client_request_id") val clientRequestId: String
)

/**
 * Respuesta 201 de POST /api/v1/pedidos (PedidoResponse.java).
 * Solo se modelan los campos que la app necesita para el dialogo de exito.
 */
data class PedidoResponseDto(
    val id: Long? = null,
    @SerializedName("mesa_id") val mesaId: Long? = null,
    val estado: String? = null,
    val subtotal: BigDecimal? = null,
    val igv: BigDecimal? = null,
    val total: BigDecimal? = null
)

fun PedidoResponseDto.aDominio(): PedidoEnviado = PedidoEnviado(
    id = id ?: 0L,
    estado = estado.orEmpty(),
    subtotal = subtotal ?: BigDecimal.ZERO,
    igv = igv ?: BigDecimal.ZERO,
    total = total ?: BigDecimal.ZERO
)
