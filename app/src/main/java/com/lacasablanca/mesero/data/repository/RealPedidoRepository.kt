package com.lacasablanca.mesero.data.repository

import com.lacasablanca.mesero.data.local.offline.OutboxPedidoStore
import com.lacasablanca.mesero.data.remote.PedidosApi
import com.lacasablanca.mesero.data.remote.dto.CrearPedidoRequest
import com.lacasablanca.mesero.data.remote.dto.ItemPedidoRequest
import com.lacasablanca.mesero.data.remote.dto.ModificadorRequest
import com.lacasablanca.mesero.data.remote.dto.aDominio
import com.lacasablanca.mesero.data.remote.dto.aPedidoActivo
import com.lacasablanca.mesero.domain.ErrorApp
import com.lacasablanca.mesero.domain.Resultado
import com.lacasablanca.mesero.domain.errorDesdeCodigoHttp
import com.lacasablanca.mesero.domain.model.ItemPedido
import com.lacasablanca.mesero.domain.model.PedidoActivo
import com.lacasablanca.mesero.domain.model.PedidoEnviado
import com.lacasablanca.mesero.domain.model.PedidoExistente
import com.lacasablanca.mesero.domain.model.TipoPedido
import retrofit2.HttpException
import java.io.IOException

/**
 * Envio real del pedido (POST /api/v1/pedidos).
 *
 * Si la peticion falla por red y hay un [outbox] configurado, el pedido se
 * guarda en la cola local (PRB-63) para que el worker de sincronizacion lo
 * reenvie cuando vuelva la conexion. La UI sigue viendo `RED` y decide como
 * informarlo.
 */
class RealPedidoRepository(
    private val api: PedidosApi,
    private val outbox: OutboxPedidoStore? = null
) : PedidoRepository {

    override suspend fun enviarPedido(
        mesaId: Long,
        items: List<ItemPedido>,
        clientRequestId: String
    ): Resultado<PedidoEnviado> {
        if (mesaId <= 0L) return Resultado.Fallo(ErrorApp.VALIDACION)

        val request = crearRequest(
            mesaId = mesaId,
            tipoPedido = TipoPedido.SALON,
            clienteNombre = null,
            items = items,
            clientRequestId = clientRequestId
        )
        if (request == null) return Resultado.Fallo(ErrorApp.VALIDACION)
        return enviar(request)
    }

    /**
     * PRB-94: pedido para llevar sin mesa. El backend lo registra con
     * `mesa_id = null` y `tipo_pedido = PARA_LLEVAR`, y no toca el salon.
     */
    override suspend fun enviarPedidoParaLlevar(
        items: List<ItemPedido>,
        clientRequestId: String,
        clienteNombre: String
    ): Resultado<PedidoEnviado> {
        val nombre = clienteNombre.trim()
        if (nombre.isEmpty()) return Resultado.Fallo(ErrorApp.VALIDACION)

        val request = crearRequest(
            mesaId = null,
            tipoPedido = TipoPedido.PARA_LLEVAR,
            clienteNombre = nombre,
            items = items,
            clientRequestId = clientRequestId
        )
        if (request == null) return Resultado.Fallo(ErrorApp.VALIDACION)
        return enviar(request)
    }

    /**
     * Construye el contrato del pedido. Devuelve null si faltan datos que el
     * backend rechazaria (items vacios o clave de idempotencia en blanco).
     */
    private fun crearRequest(
        mesaId: Long?,
        tipoPedido: TipoPedido,
        clienteNombre: String?,
        items: List<ItemPedido>,
        clientRequestId: String
    ): CrearPedidoRequest? {
        if (items.isEmpty() || clientRequestId.isBlank()) return null

        return CrearPedidoRequest(
            mesaId = mesaId,
            items = items.map { item ->
                ItemPedidoRequest(
                    productoId = item.productoId,
                    cantidad = item.cantidad,
                    notas = item.notas.takeIf { it.isNotBlank() },
                    modificadores = item.modificadores.map { modificador ->
                        ModificadorRequest(
                            nombre = modificador.nombre,
                            precioAdicional = modificador.precioAdicional
                        )
                    }
                )
            },
            clientRequestId = clientRequestId,
            tipoPedido = tipoPedido.name,
            clienteNombre = clienteNombre
        )
    }

    private suspend fun enviar(request: CrearPedidoRequest): Resultado<PedidoEnviado> =
        try {
            Resultado.Exito(api.crearPedido(request.clientRequestId, request).aDominio())
        } catch (e: HttpException) {
            // En el POST de pedidos un 409 significa que la mesa ya fue tomada
            // por otro mesero (no es el 409 de "cuenta ya pagada" del cobro).
            val error = if (e.code() == CODIGO_CONFLICTO) {
                ErrorApp.CONFLICTO_MESA
            } else {
                errorDesdeCodigoHttp(e.code())
            }
            Resultado.Fallo(error)
        } catch (e: IOException) {
            outbox?.guardar(request)
            Resultado.Fallo(ErrorApp.RED)
        } catch (e: Exception) {
            Resultado.Fallo(ErrorApp.DESCONOCIDO)
        }

    override suspend fun obtenerPedidoDeMesa(mesaId: Long): Resultado<PedidoExistente> {
        if (mesaId <= 0L) return Resultado.Fallo(ErrorApp.VALIDACION)

        return try {
            val pedido = api.obtenerPedidoDeMesa(mesaId)
            Resultado.Exito(pedido?.aDominio() ?: PedidoExistente.VACIO)
        } catch (e: HttpException) {
            // Una mesa sin pedido responde 404: no es un error de pantalla,
            // simplemente no hay lineas que mostrar en modo lectura.
            if (e.code() == CODIGO_NO_ENCONTRADO) {
                Resultado.Exito(PedidoExistente.VACIO)
            } else {
                Resultado.Fallo(errorDesdeCodigoHttp(e.code()))
            }
        } catch (e: IOException) {
            Resultado.Fallo(ErrorApp.RED)
        } catch (e: Exception) {
            Resultado.Fallo(ErrorApp.DESCONOCIDO)
        }
    }

    /**
     * PRB-88: listado de pedidos. El ViewModel filtra los estados activos.
     * Un pedido sin id valido se descarta en el mapeo.
     */
    override suspend fun listarPedidosActivos(): Resultado<List<PedidoActivo>> {
        return try {
            Resultado.Exito(api.listarPedidos().mapNotNull { it.aPedidoActivo() })
        } catch (e: HttpException) {
            Resultado.Fallo(errorDesdeCodigoHttp(e.code()))
        } catch (e: IOException) {
            Resultado.Fallo(ErrorApp.RED)
        } catch (e: Exception) {
            Resultado.Fallo(ErrorApp.DESCONOCIDO)
        }
    }

    /**
     * PRB-77: anula un plato ya enviado a cocina. Un 409 significa que el
     * plato ya fue servido y no se puede quitar de la comanda.
     */
    override suspend fun anularItem(pedidoId: Long, itemId: Long): Resultado<Unit> {
        if (pedidoId <= 0L || itemId <= 0L) return Resultado.Fallo(ErrorApp.VALIDACION)

        return try {
            val respuesta = api.anularItem(pedidoId, itemId)
            if (respuesta.isSuccessful) {
                Resultado.Exito(Unit)
            } else {
                val error = if (respuesta.code() == CODIGO_CONFLICTO) {
                    ErrorApp.PLATO_YA_SERVIDO
                } else {
                    errorDesdeCodigoHttp(respuesta.code())
                }
                Resultado.Fallo(error)
            }
        } catch (e: IOException) {
            Resultado.Fallo(ErrorApp.RED)
        } catch (e: Exception) {
            Resultado.Fallo(ErrorApp.DESCONOCIDO)
        }
    }

    private companion object {
        const val CODIGO_CONFLICTO = 409
        const val CODIGO_NO_ENCONTRADO = 404
    }
}