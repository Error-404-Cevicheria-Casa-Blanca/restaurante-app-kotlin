package com.lacasablanca.mesero.data.repository

import com.lacasablanca.mesero.data.remote.dto.CobrarRequest
import com.lacasablanca.mesero.data.remote.dto.CobroResponse
import com.lacasablanca.mesero.data.remote.dto.CuentaResponse
import com.lacasablanca.mesero.data.remote.dto.ItemCuenta
import com.lacasablanca.mesero.data.remote.dto.PedidoResumenCuenta
import com.lacasablanca.mesero.domain.ErrorApp
import com.lacasablanca.mesero.domain.Resultado
import com.lacasablanca.mesero.domain.model.EstadoMesa
import com.lacasablanca.mesero.domain.model.MetodoPago
import kotlinx.coroutines.delay
import java.math.BigDecimal

/**
 * DATOS FAKE (no reales) de la cuenta y el cobro para operar sin servidor.
 * La pantalla los rotula como "Datos de prueba" cuando USE_FAKE_DATA = true.
 * Al cobrar tambien libera la mesa en el repositorio fake, igual que el backend.
 */
class FakeCobroRepository(
    private val mesaRepository: MesaRepository? = null,
    private val latenciaMs: Long = LATENCIA_SIMULADA_MS
) : CobroRepository {

    override suspend fun obtenerCuenta(mesaId: Long): Resultado<CuentaResponse> {
        delay(latenciaMs)
        return if (mesaId <= 0L) {
            Resultado.Fallo(ErrorApp.VALIDACION)
        } else {
            Resultado.Exito(CUENTA_FAKE)
        }
    }

    override suspend fun cobrar(mesaId: Long, request: CobrarRequest): Resultado<CobroResponse> {
        delay(latenciaMs)
        if (mesaId <= 0L || request.pagos.isEmpty()) {
            return Resultado.Fallo(ErrorApp.VALIDACION)
        }

        val total = CUENTA_FAKE.total ?: BigDecimal.ZERO
        val esperado = total.add(request.propina)
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

        return Resultado.Exito(
            CobroResponse(
                total = esperado,
                pagado = pagado,
                vuelto = vuelto,
                pedidos = CUENTA_FAKE.pedidos
            )
        )
    }

    companion object {
        const val LATENCIA_SIMULADA_MS = 500L

        private val TOTAL = BigDecimal("96.00")
        private val SUBTOTAL = BigDecimal("81.36")
        private val IGV = BigDecimal("14.64")
        private val PROPINA_SUGERIDA = BigDecimal("9.60")

        val CUENTA_FAKE: CuentaResponse = CuentaResponse(
            mesaId = 1L,
            pedidos = listOf(PedidoResumenCuenta(1L, EstadoMesa.OCUPADA.valorApi, TOTAL)),
            items = listOf(
                ItemCuenta(
                    nombre = "Ceviche Clásico La Casa Blanca",
                    cantidad = 1,
                    precioUnitario = BigDecimal("48.00"),
                    subtotalLinea = BigDecimal("48.00")
                ),
                ItemCuenta(
                    nombre = "Tiradito al Ají Amarillo",
                    cantidad = 1,
                    precioUnitario = BigDecimal("28.00"),
                    subtotalLinea = BigDecimal("28.00")
                ),
                ItemCuenta(
                    nombre = "Chicha Morada 1L",
                    cantidad = 1,
                    precioUnitario = BigDecimal("20.00"),
                    subtotalLinea = BigDecimal("20.00")
                )
            ),
            subtotal = SUBTOTAL,
            igv = IGV,
            total = TOTAL,
            propinaSugerida = PROPINA_SUGERIDA
        )
    }
}
