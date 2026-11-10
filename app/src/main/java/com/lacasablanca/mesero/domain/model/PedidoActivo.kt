package com.lacasablanca.mesero.domain.model

import java.math.BigDecimal

/**
 * Origen de un pedido en la lista de Pedidos (PRB-88, Fase 6).
 * Se usa MESA (en vez de SALON) para que la UI no mezcle el contrato del POST
 * (`tipo_pedido = SALON`) con el origen visible.
 */
enum class OrigenPedido {
    MESA,
    PARA_LLEVAR,
    DELIVERY
}

/**
 * Pedido activo del salon o sin mesa.
 *
 * @property id id del pedido en el backend.
 * @property mesaId mesa asociada; null en para llevar/delivery.
 * @property clienteNombre nombre del cliente para llevar/delivery.
 * @property estado PENDIENTE, EN_PREPARACION, LISTO o SERVIDO.
 * @property total total calculado por el backend.
 */
data class PedidoActivo(
    val id: Long,
    val mesaId: Long?,
    val tipoPedido: OrigenPedido,
    val clienteNombre: String?,
    val estado: String,
    val total: BigDecimal
) {
    /** Los pedidos sin mesa se pueden cobrar desde Pedidos (Fase 6). */
    val sinMesa: Boolean get() = tipoPedido != OrigenPedido.MESA

    val puedeCobrar: Boolean get() = sinMesa
}
