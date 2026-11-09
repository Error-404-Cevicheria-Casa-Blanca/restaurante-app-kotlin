package com.lacasablanca.mesero.data.local.offline

import com.google.gson.Gson
import com.lacasablanca.mesero.data.remote.dto.CrearPedidoRequest

/** Implementacion Room del outbox: serializa el request completo como JSON. */
class RoomOutboxPedidoStore(
    private val dao: PedidoPendienteDao,
    private val gson: Gson = Gson(),
    private val reloj: () -> Long = { System.currentTimeMillis() }
) : OutboxPedidoStore {

    override suspend fun guardar(request: CrearPedidoRequest): Boolean {
        return try {
            dao.insertar(
                PedidoPendienteEntity(
                    clientRequestId = request.clientRequestId,
                    mesaId = request.mesaId,
                    payload = gson.toJson(request),
                    creadoEn = reloj()
                )
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun pendientes(): List<PedidoPendienteEntity> = dao.pendientes()

    override suspend fun eliminar(id: Long) {
        dao.eliminar(id)
    }

    override suspend fun registrarFallo(id: Long, error: String?) {
        dao.registrarFallo(id, error)
    }
}
