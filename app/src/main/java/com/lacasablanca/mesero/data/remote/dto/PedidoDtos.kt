package com.lacasablanca.mesero.data.remote.dto

import com.google.gson.annotations.SerializedName
import com.lacasablanca.mesero.domain.model.OrigenPedido
import com.lacasablanca.mesero.domain.model.PedidoActivo
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
 * PRB-94: `mesa_id` es opcional. Un pedido para llevar lo envia en `null`,
 * con `tipo_pedido = PARA_LLEVAR` y `cliente_nombre`. Un pedido de salon
 * conserva `mesa_id` y `tipo_pedido = SALON`.
 *
 * [clientRequestId] es la clave de idempotencia del pedido. Se envia tambien
 * en el header `Idempotency-Key`; el backend la usa para no duplicar el
 * pedido si el mesero reintenta tras un fallo de red (PRB-2).
 */
data class CrearPedidoRequest(
    @SerializedName("mesa_id") val mesaId: Long? = null,
    val items: List<ItemPedidoRequest>,
    @SerializedName("client_request_id") val clientRequestId: String,
    @SerializedName("tipo_pedido") val tipoPedido: String? = null,
    @SerializedName("cliente_nombre") val clienteNombre: String? = null
)

/**
 * Respuesta 201 de POST /api/v1/pedidos (PedidoResponse.java) y de
 * GET /api/v1/pedidos/{id}. Solo se modelan los campos que la app necesita
 * para el dialogo de exito y para armar la cuenta de un pedido sin mesa.
 */
data class PedidoResponseDto(
    val id: Long? = null,
    @SerializedName("mesa_id") val mesaId: Long? = null,
    val estado: String? = null,
    val subtotal: BigDecimal? = null,
    val igv: BigDecimal? = null,
    val total: BigDecimal? = null,
    @SerializedName(value = "items", alternate = ["detalle", "detalles"])
    val items: List<ItemPedidoExistenteDto>? = null,
    @SerializedName("tipo_pedido") val tipoPedido: String? = null,
    @SerializedName("cliente_nombre") val clienteNombre: String? = null,
    @SerializedName("para_llevar") val paraLlevar: Boolean? = null
)

fun PedidoResponseDto.aDominio(): PedidoEnviado = PedidoEnviado(
    id = id ?: 0L,
    estado = estado.orEmpty(),
    subtotal = subtotal ?: BigDecimal.ZERO,
    igv = igv ?: BigDecimal.ZERO,
    total = total ?: BigDecimal.ZERO
)

/**
 * PRB-88: mapea el pedido consolidado de `GET /api/v1/pedidos` al dominio de
 * la lista de Pedidos. Un pedido sin id no se puede cobrar y se descarta.
 *
 * El origen se decide por `mesa_id` y `tipo_pedido`; si el backend omite
 * `tipo_pedido` pero `para_llevar = true`, se asume PARA_LLEVAR. Nunca se
 * inventa una mesa para un pedido sin `mesa_id`.
 */
fun PedidoResponseDto.aPedidoActivo(): PedidoActivo? {
    val pedidoId = id?.takeIf { it > 0L } ?: return null
    val origen = when {
        mesaId != null -> OrigenPedido.MESA
        tipoPedido.equals("DELIVERY", ignoreCase = true) -> OrigenPedido.DELIVERY
        tipoPedido.equals("PARA_LLEVAR", ignoreCase = true) ||
            paraLlevar == true -> OrigenPedido.PARA_LLEVAR

        else -> OrigenPedido.PARA_LLEVAR
    }
    return PedidoActivo(
        id = pedidoId,
        mesaId = mesaId,
        tipoPedido = origen,
        clienteNombre = clienteNombre?.trim()?.takeIf { it.isNotEmpty() },
        estado = estado.orEmpty(),
        total = total ?: BigDecimal.ZERO
    )
}
