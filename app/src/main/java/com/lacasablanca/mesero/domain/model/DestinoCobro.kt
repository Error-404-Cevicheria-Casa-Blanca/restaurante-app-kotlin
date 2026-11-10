package com.lacasablanca.mesero.domain.model

/**
 * Destino de un cobro (PRB-88, Fase 5).
 *
 * - [Mesa]: cobro de una mesa con `POST /api/v1/mesas/{id}/cobrar`.
 * - [Pedido]: cobro de un pedido sin mesa (para llevar/delivery) con
 *   `GET /api/v1/pedidos/{id}` y `POST /api/v1/pedidos/{id}/cobrar`.
 */
sealed interface DestinoCobro {
    data class Mesa(val mesaId: Long, val numeroMesa: Int) : DestinoCobro

    data class Pedido(
        val pedidoId: Long,
        val clienteNombre: String? = null
    ) : DestinoCobro
}
