package com.lacasablanca.mesero.data.local.offline

import com.google.gson.Gson
import com.lacasablanca.mesero.data.remote.PedidosApi
import com.lacasablanca.mesero.data.remote.dto.CrearPedidoRequest
import retrofit2.HttpException
import java.io.IOException

/**
 * Logica de sincronizacion del outbox (PRB-63). La usa [SyncWorker], pero se
 * mantiene fuera de WorkManager para poder probarla con dobles de test.
 *
 * Reglas:
 * - 2xx: el pedido se envia y se elimina de la cola.
 * - 4xx: error definitivo (validacion/conflicto); se registra y se conserva
 *   para revision manual, pero no se reintenta en bucle.
 * - IOException / 5xx: se reintenta cuando vuelva la conexion.
 */
class PedidoSync(
    private val outbox: OutboxPedidoStore,
    private val api: PedidosApi,
    private val gson: Gson = Gson()
) {

    /** Devuelve true si no queda nada por reintentar. */
    suspend fun sincronizar(): Boolean {
        var reintentar = false

        for (pendiente in outbox.pendientes()) {
            val request = leer(pendiente.payload)
            if (request == null) {
                outbox.registrarFallo(pendiente.id, ERROR_PAYLOAD)
                continue
            }

            try {
                val respuesta = api.crearPedido(request.clientRequestId, request)
                if (respuesta.id != null) {
                    outbox.eliminar(pendiente.id)
                } else {
                    outbox.registrarFallo(pendiente.id, ERROR_RESPUESTA)
                    reintentar = true
                }
            } catch (e: HttpException) {
                outbox.registrarFallo(pendiente.id, "HTTP ${e.code()}")
                if (e.code() >= CODIGO_SERVIDOR) reintentar = true
            } catch (e: IOException) {
                outbox.registrarFallo(pendiente.id, ERROR_RED)
                reintentar = true
            } catch (e: Exception) {
                outbox.registrarFallo(pendiente.id, e.message)
                reintentar = true
            }
        }

        return !reintentar
    }

    private fun leer(payload: String): CrearPedidoRequest? = runCatching {
        gson.fromJson(payload, CrearPedidoRequest::class.java)
    }.getOrNull()

    companion object {
        const val ERROR_PAYLOAD = "payload_invalido"
        const val ERROR_RESPUESTA = "respuesta_sin_id"
        const val ERROR_RED = "RED"
        private const val CODIGO_SERVIDOR = 500
    }
}
