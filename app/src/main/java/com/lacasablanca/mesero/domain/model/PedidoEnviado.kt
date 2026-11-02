package com.lacasablanca.mesero.domain.model

import java.math.BigDecimal

/**
 * Respuesta de POST /api/v1/pedidos (201 Created).
 * El backend devuelve el total calculado y su desglose (D13).
 */
data class PedidoEnviado(
    val id: Long,
    val estado: String,
    val subtotal: BigDecimal,
    val igv: BigDecimal,
    val total: BigDecimal
)
