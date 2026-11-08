package com.lacasablanca.mesero.data.repository

import com.lacasablanca.mesero.domain.Resultado
import com.lacasablanca.mesero.domain.model.ItemPedido
import com.lacasablanca.mesero.domain.model.PedidoEnviado
import com.lacasablanca.mesero.domain.model.PedidoExistente

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

    /**
     * PRB-83: pedido ya registrado en una mesa OCUPADA
     * (`GET /api/v1/pedidos?mesa_id=X`). El fake permite probar la UI sin
     * backend mientras PRB-82 no este cerrado.
     */
    suspend fun obtenerPedidoDeMesa(mesaId: Long): Resultado<PedidoExistente>
}
