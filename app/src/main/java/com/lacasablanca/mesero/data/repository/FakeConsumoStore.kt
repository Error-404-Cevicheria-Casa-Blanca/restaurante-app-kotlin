package com.lacasablanca.mesero.data.repository

import java.math.BigDecimal

/**
 * DATOS FAKE (no reales). Acumula el consumo por mesa durante la sesion
 * para que la cuenta simulada coincida con los pedidos enviados.
 */
class FakeConsumoStore {

    private val totales = mutableMapOf<Long, BigDecimal>()

    fun registrar(mesaId: Long, total: BigDecimal) {
        totales[mesaId] = acumular(totales[mesaId], total)
    }

    fun total(mesaId: Long): BigDecimal? = totales[mesaId]

    fun limpiar(mesaId: Long) {
        totales.remove(mesaId)
    }

    private fun acumular(actual: BigDecimal?, nuevo: BigDecimal): BigDecimal =
        (actual ?: BigDecimal.ZERO).add(nuevo)
}
