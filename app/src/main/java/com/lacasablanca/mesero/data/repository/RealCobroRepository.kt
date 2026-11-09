package com.lacasablanca.mesero.data.repository

import com.lacasablanca.mesero.data.remote.CobroApi
import com.lacasablanca.mesero.data.remote.dto.CobrarRequest
import com.lacasablanca.mesero.data.remote.dto.CobroResponse
import com.lacasablanca.mesero.data.remote.dto.CuentaResponse
import com.lacasablanca.mesero.data.remote.dto.ItemCuenta
import com.lacasablanca.mesero.data.remote.dto.PedidoResponseDto
import com.lacasablanca.mesero.data.remote.dto.PedidoResumenCuenta
import com.lacasablanca.mesero.data.remote.dto.TrabajoImpresionDto
import com.lacasablanca.mesero.domain.ErrorApp
import com.lacasablanca.mesero.domain.Resultado
import com.lacasablanca.mesero.domain.errorDesdeCodigoHttp
import retrofit2.Response
import java.io.IOException
import java.math.BigDecimal
import java.math.RoundingMode

/** Cuenta y cobro contra la API real (PRB-55 / PRB-88). */
class RealCobroRepository(private val api: CobroApi) : CobroRepository {

    override suspend fun obtenerCuenta(mesaId: Long): Resultado<CuentaResponse> =
        ejecutar { api.obtenerCuenta(mesaId) }

    override suspend fun cobrar(mesaId: Long, request: CobrarRequest): Resultado<CobroResponse> =
        ejecutar(errorConflicto = ErrorApp.CONFLICTO_COBRO) { api.cobrar(mesaId, request) }

    /**
     * PRB-88: cuenta de un pedido sin mesa. Se arma con el pedido consolidado
     * (`GET /api/v1/pedidos/{id}`) y se mapea al mismo [CuentaResponse] que
     * usa la pantalla de cobro.
     */
    override suspend fun obtenerCuentaPedido(pedidoId: Long): Resultado<CuentaResponse> {
        if (pedidoId <= 0L) return Resultado.Fallo(ErrorApp.VALIDACION)

        return try {
            val respuesta = api.obtenerPedido(pedidoId)
            if (respuesta.isSuccessful) {
                val cuerpo = respuesta.body()
                if (cuerpo != null) {
                    Resultado.Exito(cuerpo.aCuenta())
                } else {
                    Resultado.Fallo(ErrorApp.SERVIDOR)
                }
            } else {
                Resultado.Fallo(errorDesdeCodigoHttp(respuesta.code()))
            }
        } catch (e: IOException) {
            Resultado.Fallo(ErrorApp.RED)
        } catch (e: Exception) {
            Resultado.Fallo(ErrorApp.DESCONOCIDO)
        }
    }

    /** PRB-88: cobra un pedido sin mesa; no toca el estado de ninguna mesa. */
    override suspend fun cobrarPedido(
        pedidoId: Long,
        request: CobrarRequest
    ): Resultado<CobroResponse> =
        ejecutar(errorConflicto = ErrorApp.CONFLICTO_COBRO) {
            api.cobrarPedido(pedidoId, request)
        }

    override suspend fun imprimirPrecuenta(mesaId: Long): Resultado<CuentaResponse> =
        ejecutar { api.imprimirPrecuenta(mesaId) }

    override suspend fun reimprimirPrecuenta(mesaId: Long): Resultado<TrabajoImpresionDto> =
        ejecutar { api.reimprimirPrecuenta(mesaId) }

    /** Mapea el pedido consolidado a la cuenta que dibuja CobroFragment. */
    private fun PedidoResponseDto.aCuenta(): CuentaResponse {
        val totalPedido = total ?: BigDecimal.ZERO
        val lineas = items.orEmpty().mapNotNull { item ->
            val cantidad = item.cantidad?.takeIf { it > 0 } ?: return@mapNotNull null
            val precio = item.precioUnitario ?: BigDecimal.ZERO
            val subtotalLinea = item.subtotal
                ?: precio.multiply(BigDecimal.valueOf(cantidad.toLong()))
                    .setScale(2, RoundingMode.HALF_UP)
            ItemCuenta(
                nombre = item.nombre.orEmpty(),
                cantidad = cantidad,
                precioUnitario = precio,
                subtotalLinea = subtotalLinea
            )
        }

        return CuentaResponse(
            mesaId = mesaId,
            pedidos = listOf(
                PedidoResumenCuenta(
                    id = id,
                    estado = estado,
                    total = totalPedido
                )
            ),
            items = lineas,
            subtotal = subtotal,
            igv = igv,
            total = totalPedido,
            propinaSugerida = null
        )
    }

    /**
     * [errorConflicto] distingue el significado del 409: en el cobro es
     * "la cuenta ya fue pagada"; en otros endpoints es un conflicto generico.
     */
    private suspend fun <T> ejecutar(
        errorConflicto: ErrorApp = ErrorApp.CONFLICTO,
        llamada: suspend () -> Response<T>
    ): Resultado<T> {
        return try {
            val respuesta = llamada()
            if (respuesta.isSuccessful) {
                val cuerpo = respuesta.body()
                if (cuerpo != null) {
                    Resultado.Exito(cuerpo)
                } else {
                    Resultado.Fallo(ErrorApp.SERVIDOR)
                }
            } else {
                val error = if (respuesta.code() == CODIGO_CONFLICTO) {
                    errorConflicto
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
    }
}
