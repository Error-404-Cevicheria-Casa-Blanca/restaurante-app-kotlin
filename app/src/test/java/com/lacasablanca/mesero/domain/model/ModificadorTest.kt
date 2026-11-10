package com.lacasablanca.mesero.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/** PRB-92: reglas de precio de los modificadores. */
class ModificadorTest {

    @Test
    fun `suma los recargos con dos decimales`() {
        val total = Modificador.recargoTotal(
            listOf(
                Modificador("Extra queso", BigDecimal("2.00")),
                Modificador("Extra canchita", BigDecimal("1.50")),
                Modificador("Sin cebolla", BigDecimal("0.00"))
            )
        )

        assertEquals("3.50", total.toPlainString())
    }

    @Test
    fun `un precio negativo se ignora por seguridad`() {
        val total = Modificador.recargoTotal(
            listOf(
                Modificador("Extra queso", BigDecimal("2.00")),
                Modificador("Descuento manipulado", BigDecimal("-5.00"))
            )
        )

        assertEquals("2.00", total.toPlainString())
    }

    @Test
    fun `un modificador sin precio es gratuito`() {
        assertTrue(Modificador("Sin cebolla", BigDecimal.ZERO).esGratuito)
        assertTrue(Modificador("Sin limón").esGratuito)
        assertFalse(Modificador("Extra queso", BigDecimal("2.00")).esGratuito)
    }
}
