package com.lacasablanca.mesero.data.repository

import com.lacasablanca.mesero.data.remote.dto.CobrarRequest
import com.lacasablanca.mesero.data.remote.dto.CobroResponse
import com.lacasablanca.mesero.data.remote.dto.CuentaResponse
import com.lacasablanca.mesero.data.remote.dto.ItemCuenta
import com.lacasablanca.mesero.data.remote.dto.PedidoResumenCuenta
import com.lacasablanca.mesero.data.remote.dto.TrabajoImpresionDto
import com.lacasablanca.mesero.domain.ErrorApp
import com.lacasablanca.mesero.domain.Resultado
import com.lacasablanca.mesero.domain.model.CalculoIgv
import com.lacasablanca.mesero.domain.model.EstadoMesa
import com.lacasablanca.mesero.domain.model.MetodoPago
import kotlinx.coroutines.delay
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * DATOS FAKE (no reales) de la cuenta y el cobro.
 * La cuenta se arma con el consumo acumulado por mesa para que el total
 * coincida con los pedidos enviados; si no hay consumo usa un total de prueba.
 */
class FakeCobroRepository(
    private val mesaRepository: MesaRepository? = null,
    private val consumoStore: FakeConsumoStore? = null,
    private val latenciaMs: Long = LATENCIA_SIMULADA_MS
) : CobroRepository {

    override suspend fun obtenerCuenta(mesaId: Long): Resultado<CuentaResponse> {
        delay(latenciaMs)
        return if (mesaId <= 0L) {
            Resultado.Fallo(ErrorApp.VALIDACION)
        } else {
            Resultado.Exito(cuentaDe(mesaId))
        }
    }

    override suspend fun cobrar(mesaId: Long, request: CobrarRequest): Resultado<CobroResponse> {
        delay(latenciaMs)
        if (mesaId <= 0L || request.pagos.isEmpty()) {
            return Resultado.Fallo(ErrorApp.VALIDACION)
        }

        val cuenta = cuentaDe(mesaId)
        val totalCuenta = cuenta.total ?: BigDecimal.ZERO
        val descuento = request.descuento.setScale(2, RoundingMode.HALF_UP)
        if (descuento < BigDecimal.ZERO || descuento > totalCuenta) {
            return Resultado.Fallo(ErrorApp.VALIDACION)
        }
        val esperado = totalCuenta.subtract(descuento).add(request.propina)
        val pagado = request.pagos.fold(BigDecimal.ZERO) { acumulado, pago ->
            acumulado.add(pago.monto)
        }
        if (pagado.compareTo(esperado) != 0) {
            return Resultado.Fallo(ErrorApp.VALIDACION)
        }

        val efectivo = request.pagos
            .filter { it.metodo == MetodoPago.EFECTIVO.valorApi }
            .fold(BigDecimal.ZERO) { acumulado, pago -> acumulado.add(pago.monto) }
        val vuelto = efectivo.subtract(esperado).max(BigDecimal.ZERO)

        mesaRepository?.cambiarEstado(mesaId, EstadoMesa.LIBRE)
        consumoStore?.limpiar(mesaId)

        return Resultado.Exito(
            CobroResponse(
                total = esperado,
                pagado = pagado,
                vuelto = vuelto,
                pedidos = cuenta.pedidos
            )
        )
    }

    override suspend fun imprimirPrecuenta(mesaId: Long): Resultado<CuentaResponse> {
        delay(latenciaMs)
        return if (mesaId <= 0L) {
            Resultado.Fallo(ErrorApp.VALIDACION)
        } else {
            Resultado.Exito(cuentaDe(mesaId))
        }
    }

    override suspend fun reimprimirPrecuenta(mesaId: Long): Resultado<TrabajoImpresionDto> {
        delay(latenciaMs)
        if (mesaId <= 0L) return Resultado.Fallo(ErrorApp.VALIDACION)
        return Resultado.Exito(
            TrabajoImpresionDto(
                jobId = "fake-precuenta-$mesaId",
                mesaId = mesaId,
                tipo = FAKE_TIPO_IMPRESION,
                estado = FAKE_ESTADO_IMPRESION,
                intentos = 0,
                mensajeError = null,
                fechaSolicitud = null
            )
        )
    }

    private fun cuentaDe(mesaId: Long): CuentaResponse {
        val desglose = CalculoIgv.desglosar(totalDe(mesaId))
        val propina = desglose.total
            .multiply(PROPINA_SUGERIDA)
            .setScale(2, RoundingMode.HALF_UP)
        return CuentaResponse(
            mesaId = mesaId,
            pedidos = listOf(
                PedidoResumenCuenta(
                    id = 1L,
                    estado = EstadoMesa.CUENTA_SOLICITADA.valorApi,
                    total = desglose.total
                )
            ),
            items = listOf(
                ItemCuenta(
                    nombre = ITEM_GENERICO,
                    cantidad = 1,
                    precioUnitario = desglose.total,
                    subtotalLinea = desglose.total
                )
            ),
            subtotal = desglose.subtotal,
            igv = desglose.igv,
            total = desglose.total,
            propinaSugerida = propina
        )
    }

    private fun totalDe(mesaId: Long): BigDecimal =
        consumoStore?.total(mesaId) ?: TOTAL_POR_DEFECTO

    companion object {
        const val LATENCIA_SIMULADA_MS = 500L
        private const val ITEM_GENERICO = "Consumo de Mesa"

        /** DATO FAKE: rotula el trabajo de impresion simulado. */
        private const val FAKE_TIPO_IMPRESION = "PRECUENTA_80MM"

        /** DATO FAKE: estado simulado de la cola de impresion. */
        private const val FAKE_ESTADO_IMPRESION = "PENDIENTE"
        private val TOTAL_POR_DEFECTO = BigDecimal("96.00")
        private val PROPINA_SUGERIDA = BigDecimal("0.10")
    }
}
