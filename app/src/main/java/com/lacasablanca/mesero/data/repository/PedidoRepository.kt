package com.lacasablanca.mesero.data.repository

import com.lacasablanca.mesero.domain.Resultado
import com.lacasablanca.mesero.domain.model.ItemPedido
import com.lacasablanca.mesero.domain.model.PedidoEnviado

/** Unica fuente de datos para enviar la comanda (POST /api/v1/pedidos). */
interface PedidoRepository {
    /**
     * [clientRequestId] es la clave de idempotencia: se genera una vez por
     * intento de pedido y se reutiliza al reintentar tras un fallo de red.
     */
    suspend fun enviarPedido(
        mesaId: Long,
        items: List<ItemPedido>,
        clientRequestId: String
    ): Resultado<PedidoEnviado>
}
