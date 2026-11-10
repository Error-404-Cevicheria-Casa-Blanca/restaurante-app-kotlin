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
                    // PRB-94: un pedido para llevar no tiene mesa. La columna
                    // historica es NOT NULL, asi que se guarda 0 como "sin
                    // mesa"; el payload conserva el contrato real (mesa_id
                    // ausente y tipo_pedido = PARA_LLEVAR).
                    mesaId = request.mesaId ?: SIN_MESA,
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

    private companion object {
        /** Valor historico para pedidos sin mesa (PRB-94). */
        const val SIN_MESA = 0L
    }
}
