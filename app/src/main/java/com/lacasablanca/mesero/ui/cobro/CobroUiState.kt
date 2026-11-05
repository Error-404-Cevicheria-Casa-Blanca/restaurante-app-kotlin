package com.lacasablanca.mesero.ui.cobro

import com.lacasablanca.mesero.data.remote.dto.CuentaResponse
import com.lacasablanca.mesero.data.remote.dto.PagoRequest
import com.lacasablanca.mesero.domain.model.MetodoPago
import java.math.BigDecimal

/**
 * Estado de la pantalla de cobro.
 * `error` viaja como codigo para que los textos visibles sigan en strings.xml.
 */
data class CobroUiState(
    val cuenta: CuentaResponse? = null,
    val pagos: List<PagoRequest> = emptyList(),
    val propina: BigDecimal = BigDecimal.ZERO,
    val montoRecibido: String = "",
    val vuelto: BigDecimal = BigDecimal.ZERO,
    val loading: Boolean = false,
    val error: String? = null,
    val exito: Boolean = false,
    val confirmarHabilitado: Boolean = false,
    val metodoSeleccionado: MetodoPago = MetodoPago.EFECTIVO,
    val referencia: String = ""
) {
    val total: BigDecimal get() = cuenta?.total ?: BigDecimal.ZERO

    val totalConPropina: BigDecimal get() = total.add(propina)

    val totalPagado: BigDecimal get() = pagos.fold(BigDecimal.ZERO) { acumulado, pago ->
        acumulado.add(pago.monto)
    }

    /** Recalcula si el cobro se puede confirmar: pagos == total + propina. */
    fun calcularTotales(): CobroUiState {
        val habilitado = pagos.isNotEmpty() &&
            totalConPropina > BigDecimal.ZERO &&
            totalPagado.compareTo(totalConPropina) == 0
        return copy(confirmarHabilitado = habilitado)
    }
}
