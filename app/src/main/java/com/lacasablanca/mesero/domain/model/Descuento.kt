package com.lacasablanca.mesero.domain.model

import java.math.BigDecimal
import java.math.RoundingMode

/** Como se expresa el descuento que el mesero aplica a la cuenta. */
enum class TipoDescuento {
    PORCENTAJE,
    MONTO_FIJO
}

/**
 * Descuento aplicado al total de la cuenta (PRB-80).
 *
 * El total de la cuenta ya incluye IGV (D13), asi que el porcentaje se
 * calcula sobre ese total y nunca puede dejar la cuenta en negativo.
 * Todo el dinero se opera con [BigDecimal] y dos decimales.
 */
data class Descuento(
    val tipo: TipoDescuento,
    val valor: BigDecimal
) {

    /** Monto en soles que se resta de [base], acotado entre 0 y [base]. */
    fun montoSobre(base: BigDecimal): BigDecimal {
        if (base <= BigDecimal.ZERO) return BigDecimal.ZERO
        val calculado = when (tipo) {
            TipoDescuento.PORCENTAJE ->
                base.multiply(valor).divide(CIEN, 2, RoundingMode.HALF_UP)

            TipoDescuento.MONTO_FIJO -> valor
        }.setScale(2, RoundingMode.HALF_UP)
        return calculado.max(BigDecimal.ZERO).min(base)
    }

    private companion object {
        val CIEN: BigDecimal = BigDecimal("100")
    }
}
