package com.lacasablanca.mesero.data.repository

import com.lacasablanca.mesero.data.remote.PedidosApi
import com.lacasablanca.mesero.data.remote.dto.CrearPedidoRequest
import com.lacasablanca.mesero.data.remote.dto.ItemPedidoRequest
import com.lacasablanca.mesero.data.remote.dto.aDominio
import com.lacasablanca.mesero.domain.ErrorApp
import com.lacasablanca.mesero.domain.Resultado
import com.lacasablanca.mesero.domain.errorDesdeCodigoHttp
import com.lacasablanca.mesero.domain.model.ItemPedido
import com.lacasablanca.mesero.domain.model.PedidoEnviado
import com.lacasablanca.mesero.domain.model.PedidoExistente
import retrofit2.HttpException
import java.io.IOException

/** Envio real del pedido (POST /api/v1/pedidos). */
class RealPedidoRepository(private val api: PedidosApi) : PedidoRepository {

    override suspend fun enviarPedido(
        mesaId: Long,
        items: List<ItemPedido>,
        clientRequestId: String
    ): Resultado<PedidoEnviado> {
        if (mesaId <= 0L || items.isEmpty() || clientRequestId.isBlank()) {
            return Resultado.Fallo(ErrorApp.VALIDACION)
        }

        return try {
            val request = CrearPedidoRequest(
                mesaId = mesaId,
                items = items.map { item ->
                    ItemPedidoRequest(
                        productoId = item.productoId,
                        cantidad = item.cantidad,
                        notas = item.notas.takeIf { it.isNotBlank() }
                    )
                },
                clientRequestId = clientRequestId
            )
            Resultado.Exito(api.crearPedido(clientRequestId, request).aDominio())
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
            Resultado.Fallo(ErrorApp.RED)
        } catch (e: Exception) {
            Resultado.Fallo(ErrorApp.DESCONOCIDO)
        }
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

    private companion object {
        const val CODIGO_CONFLICTO = 409
        const val CODIGO_NO_ENCONTRADO = 404
    }
}