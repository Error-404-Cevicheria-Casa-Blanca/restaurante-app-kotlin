package com.lacasablanca.mesero.ui.common

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Formato de dinero de la UI (soles peruanos): dos decimales y punto,
 * igual que los disenos ("S/ 48.00"). La app es mono-moneda (PEN).
 */
fun formatearMoneda(monto: BigDecimal): String =
    "S/ " + monto.setScale(2, RoundingMode.HALF_UP).toPlainString()
