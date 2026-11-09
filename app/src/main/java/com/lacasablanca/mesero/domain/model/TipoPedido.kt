package com.lacasablanca.mesero.domain.model

/**
 * Tipos de pedido que acepta `POST /api/v1/pedidos` (verificado en
 * `CrearPedidoRequest.java` y en `PedidoParaLlevarDeliveryIntegrationTest`).
 *
 * - [SALON]: pedido asociado a una mesa (`mesa_id` obligatorio).
 * - [PARA_LLEVAR]: pedido sin mesa; identifica al cliente por nombre.
 * - [DELIVERY]: reservado por el backend para pedidos con dirección.
 */
enum class TipoPedido {
    SALON,
    PARA_LLEVAR,
    DELIVERY
}
