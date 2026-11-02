package com.lacasablanca.mesero.domain.model

import java.math.BigDecimal

/**
 * Instantanea de una linea del carrito que viaja al repositorio de pedidos.
 * `precioUnitario` NO se envia al backend (el backend recalcula con su BD);
 * se conserva aqui para que el repositorio Fake pueda calcular el total.
 */
data class ItemPedido(
    val productoId: Long,
    val cantidad: Int,
    val notas: String,
    val precioUnitario: BigDecimal
)
