package com.lacasablanca.mesero.data.local.offline

import com.lacasablanca.mesero.data.remote.dto.CrearPedidoRequest

/**
 * Contrato de la cola de pedidos pendientes. Se abstrae del DAO para poder
 * probar el envio y el sincronizador sin Android.
 */
interface OutboxPedidoStore {

    /** Guarda el pedido; devuelve false si no se pudo persistir. */
    suspend fun guardar(request: CrearPedidoRequest): Boolean

    suspend fun pendientes(): List<PedidoPendienteEntity>

    suspend fun eliminar(id: Long)

    suspend fun registrarFallo(id: Long, error: String?)
}
