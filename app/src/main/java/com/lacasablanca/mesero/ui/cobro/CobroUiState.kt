package com.lacasablanca.mesero.ui.cobro

import com.lacasablanca.mesero.data.remote.dto.CuentaResponse
import com.lacasablanca.mesero.data.remote.dto.PagoRequest
import com.lacasablanca.mesero.domain.model.CalculoIgv
import com.lacasablanca.mesero.domain.model.Descuento
import com.lacasablanca.mesero.domain.model.DesglosePedido
import com.lacasablanca.mesero.domain.model.MetodoPago
import java.math.BigDecimal
import java.math.RoundingMode

data class CuotaComensal(
    val numero: Int,
    val monto: BigDecimal
)

/**
 * Estado de la pantalla de cobro.
 * `error` viaja como codigo para que los textos visibles sigan en strings.xml.
 */
data class CobroUiState(
    val cuenta: CuentaResponse? = null,
    val pagos: List<PagoRequest> = emptyList(),
    val propina: BigDecimal = BigDecimal.ZERO,
    val montoPago: String = "",
    val vuelto: BigDecimal = BigDecimal.ZERO,
    val loading: Boolean = false,
    val error: String? = null,
    val exito: Boolean = false,
    val confirmarHabilitado: Boolean = false,
    val metodoSeleccionado: MetodoPago = MetodoPago.EFECTIVO,
    val referencia: String = "",
    val dividirCuenta: Boolean = false,
    val numeroComensales: Int = 0,
    val cuotas: List<CuotaComensal> = emptyList(),
    val descuento: Descuento? = null
) {
    /** Total de la cuenta tal como lo devuelve el backend, antes del descuento. */
    val totalBruto: BigDecimal get() = cuenta?.total ?: BigDecimal.ZERO

    val montoDescuento: BigDecimal get() = descuento?.montoSobre(totalBruto) ?: BigDecimal.ZERO

    /** Total a cobrar con el descuento aplicado (nunca negativo). */
    val total: BigDecimal get() = totalBruto.subtract(montoDescuento).max(BigDecimal.ZERO)

    /** Subtotal e IGV recalculados sobre el total con descuento (D13). */
    val desglose: DesglosePedido get() = CalculoIgv.desglosar(total)

    val subtotal: BigDecimal get() = desglose.subtotal

    val igv: BigDecimal get() = desglose.igv

    val totalConPropina: BigDecimal get() = total.add(propina)

    val totalPagado: BigDecimal get() = pagos.fold(BigDecimal.ZERO) { acumulado, pago ->
        acumulado.add(pago.monto)
    }

    val saldoPendiente: BigDecimal get() =
        (totalConPropina - totalPagado).max(BigDecimal.ZERO)

    /** Recalcula si el cobro se puede confirmar y las cuotas de la division. */
    fun calcularTotales(): CobroUiState {
        val habilitado = pagos.isNotEmpty() &&
            totalConPropina > BigDecimal.ZERO &&
            totalPagado.compareTo(totalConPropina) == 0
        val cuotasActuales = if (dividirCuenta) {
            calcularCuotas(totalConPropina, numeroComensales)
        } else {
            emptyList()
        }
        return copy(confirmarHabilitado = habilitado, cuotas = cuotasActuales)
    }

    companion object {
        fun calcularCuotas(total: BigDecimal, personas: Int): List<CuotaComensal> {
            if (personas <= 0) return emptyList()
            val totalCentimos = total
                .setScale(2, RoundingMode.HALF_UP)
                .movePointRight(2)
                .longValueExact()
            val base = totalCentimos / personas
            val residuo = (totalCentimos % personas).toInt()
            val primeraCuotaAjustada = personas - residuo + 1
            return (1..personas).map { numero ->
                val centimos = base + if (numero >= primeraCuotaAjustada) 1L else 0L
                CuotaComensal(
                    numero = numero,
                    monto = BigDecimal.valueOf(centimos)
                        .movePointLeft(2)
                        .setScale(2, RoundingMode.HALF_UP)
                )
            }
        }
    }
}
