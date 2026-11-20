package com.lacasablanca.mesero.data.local.offline

import com.google.gson.Gson
import com.lacasablanca.mesero.data.remote.PedidosApi
import com.lacasablanca.mesero.data.remote.dto.CrearPedidoRequest
import com.lacasablanca.mesero.domain.model.ErroresOutbox
import retrofit2.HttpException
import java.io.IOException

/**
 * Logica de sincronizacion del outbox (PRB-63). La usa [SyncWorker], pero se
 * mantiene fuera de WorkManager para poder probarla con dobles de test.
 *
 * Reglas:
 * - 2xx: el pedido se envia y se elimina de la cola.
 * - 401: la sesion ya no vale; se detiene la cola y se conserva.
 * - 409: conflicto definitivo; se conserva y la UI ofrece reintentar/descartar.
 * - 4xx: error definitivo (validacion); se registra y no se reintenta.
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

            when (enviar(pendiente.id, request)) {
                ResultadoEnvio.OK -> outbox.eliminar(pendiente.id)
                ResultadoEnvio.REINTENTAR -> reintentar = true
                ResultadoEnvio.SESION -> return true
                ResultadoEnvio.DEFINITIVO -> Unit
            }
        }

        return !reintentar
    }

    private suspend fun enviar(
        id: Long,
        request: CrearPedidoRequest
    ): ResultadoEnvio = try {
        val respuesta = api.crearPedido(request.clientRequestId, request)
        if (respuesta.id != null) {
            ResultadoEnvio.OK
        } else {
            outbox.registrarFallo(id, ERROR_RESPUESTA)
            ResultadoEnvio.REINTENTAR
        }
    } catch (e: HttpException) {
        manejarHttp(id, e.code())
    } catch (e: IOException) {
        outbox.registrarFallo(id, ERROR_RED)
        ResultadoEnvio.REINTENTAR
    } catch (e: Exception) {
        outbox.registrarFallo(id, e.message)
        ResultadoEnvio.REINTENTAR
    }

    private suspend fun manejarHttp(id: Long, codigo: Int): ResultadoEnvio {
        if (codigo == CODIGO_NO_AUTORIZADO) {
            outbox.registrarFallo(id, ErroresOutbox.SESION_EXPIRADA)
            return ResultadoEnvio.SESION
        }
        if (codigo == CODIGO_CONFLICTO) {
            outbox.registrarFallo(id, ErroresOutbox.CONFLICTO)
            return ResultadoEnvio.DEFINITIVO
        }
        outbox.registrarFallo(id, "HTTP $codigo")
        return if (codigo >= CODIGO_SERVIDOR) {
            ResultadoEnvio.REINTENTAR
        } else {
            ResultadoEnvio.DEFINITIVO
        }
    }

    private fun leer(payload: String): CrearPedidoRequest? = runCatching {
        gson.fromJson(payload, CrearPedidoRequest::class.java)
    }.getOrNull()

    private enum class ResultadoEnvio { OK, REINTENTAR, DEFINITIVO, SESION }

    companion object {
        const val ERROR_PAYLOAD = "payload_invalido"
        const val ERROR_RESPUESTA = "respuesta_sin_id"
        const val ERROR_RED = ErroresOutbox.RED
        private const val CODIGO_NO_AUTORIZADO = 401
        private const val CODIGO_CONFLICTO = 409
        private const val CODIGO_SERVIDOR = 500
    }
}
