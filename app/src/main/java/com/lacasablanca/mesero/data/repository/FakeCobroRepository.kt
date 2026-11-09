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
 * La cuenta de mesa se arma con el consumo acumulado; la de un pedido sin
 * mesa usa un detalle de demostracion rotulado como fake.
 */
class FakeCobroRepository(
    private val mesaRepository: MesaRepository? = null,
    private val consumoStore: FakeConsumoStore? = null,
    private val latenciaMs: Long = LATENCIA_SIMULADA_MS,
    private val pedidoRepository: FakePedidoRepository? = null
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

    /**
     * DATOS FAKE (no reales): cuenta de un pedido para llevar/delivery.
     * No consulta el estado de ninguna mesa.
     */
    override suspend fun obtenerCuentaPedido(pedidoId: Long): Resultado<CuentaResponse> {
        delay(latenciaMs)
        return if (pedidoId <= 0L) {
            Resultado.Fallo(ErrorApp.VALIDACION)
        } else {
            Resultado.Exito(cuentaPedidoDe(pedidoId))
        }
    }

    /**
     * DATOS FAKE (no reales): cobra un pedido sin mesa. Ignora el descuento
     * (la UI lo oculta en modo pedido, A87) y no libera ninguna mesa.
     */
    override suspend fun cobrarPedido(
        pedidoId: Long,
        request: CobrarRequest
    ): Resultado<CobroResponse> {
        delay(latenciaMs)
        if (pedidoId <= 0L || request.pagos.isEmpty()) {
            return Resultado.Fallo(ErrorApp.VALIDACION)
        }

        val cuenta = cuentaPedidoDe(pedidoId)
        val totalCuenta = cuenta.total ?: BigDecimal.ZERO
        val esperado = totalCuenta.add(request.propina)
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

        // Fase 6: el pedido cobrado desaparece de la lista de Pedidos.
        pedidoRepository?.marcarCobrado(pedidoId)

        return Resultado.Exito(
            CobroResponse(
                total = esperado,
                pagado = pagado,
                vuelto = vuelto,
                pedidos = listOf(
                    PedidoResumenCuenta(
                        id = pedidoId,
                        estado = ESTADO_COBRADO,
                        total = totalCuenta
                    )
                )
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

    /** DATOS FAKE (no reales): 48.00 + 12.00 = 60.00. */
    private fun cuentaPedidoDe(pedidoId: Long): CuentaResponse {
        val lineas = listOf(
            ItemCuenta(
                nombre = "Ceviche Clásico",
                cantidad = 1,
                precioUnitario = BigDecimal("48.00"),
                subtotalLinea = BigDecimal("48.00")
            ),
            ItemCuenta(
                nombre = "Chicha Morada",
                cantidad = 1,
                precioUnitario = BigDecimal("12.00"),
                subtotalLinea = BigDecimal("12.00")
            )
        )
        val total = lineas.fold(BigDecimal.ZERO) { acumulado, linea ->
            acumulado.add(linea.subtotalLinea ?: BigDecimal.ZERO)
        }
        val desglose = CalculoIgv.desglosar(total)
        return CuentaResponse(
            mesaId = null,
            pedidos = listOf(
                PedidoResumenCuenta(
                    id = pedidoId,
                    estado = ESTADO_PENDIENTE,
                    total = desglose.total
                )
            ),
            items = lineas,
            subtotal = desglose.subtotal,
            igv = desglose.igv,
            total = desglose.total,
            propinaSugerida = null
        )
    }

    private fun totalDe(mesaId: Long): BigDecimal =
        consumoStore?.total(mesaId) ?: TOTAL_POR_DEFECTO

    companion object {
        const val LATENCIA_SIMULADA_MS = 500L
        private const val ITEM_GENERICO = "Consumo de Mesa"
        private const val ESTADO_PENDIENTE = "PENDIENTE"
        private const val ESTADO_COBRADO = "COBRADO"

        /** DATO FAKE: rotula el trabajo de impresion simulado. */
        private const val FAKE_TIPO_IMPRESION = "PRECUENTA_80MM"

        /** DATO FAKE: estado simulado de la cola de impresion. */
        private const val FAKE_ESTADO_IMPRESION = "PENDIENTE"
        private val TOTAL_POR_DEFECTO = BigDecimal("96.00")
        private val PROPINA_SUGERIDA = BigDecimal("0.10")
    }
}
