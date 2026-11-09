package com.lacasablanca.mesero.domain.model

/**
 * Destino de una comanda. La app reutiliza el mismo catalogo y carrito para
 * ambos flujos; solo cambia el `POST /api/v1/pedidos`:
 *
 * - [Mesa] envia `mesa_id` y `tipo_pedido = SALON`.
 * - [ParaLlevar] envia `mesa_id = null`, `tipo_pedido = PARA_LLEVAR` y el
 *   nombre del cliente (PRB-79/PRB-94).
 */
sealed interface DestinoPedido {
    data class Mesa(val mesaId: Long) : DestinoPedido

    data class ParaLlevar(val clienteNombre: String) : DestinoPedido
}
