package com.lacasablanca.mesero.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** PRB-76: mapeo del estado de preparacion que llega del API. */
class EstadoPlatoTest {

    @Test
    fun `mapea PENDIENTE`() {
        assertEquals(EstadoPlato.PENDIENTE, EstadoPlato.desdeApi("PENDIENTE"))
    }

    @Test
    fun `mapea EN_PREPARACION`() {
        assertEquals(EstadoPlato.EN_PREPARACION, EstadoPlato.desdeApi("EN_PREPARACION"))
    }

    @Test
    fun `mapea LISTO`() {
        assertEquals(EstadoPlato.LISTO, EstadoPlato.desdeApi("LISTO"))
    }

    @Test
    fun `mapea SERVIDO`() {
        assertEquals(EstadoPlato.SERVIDO, EstadoPlato.desdeApi("SERVIDO"))
    }

    @Test
    fun `mapea ANULADO`() {
        assertEquals(EstadoPlato.ANULADO, EstadoPlato.desdeApi("ANULADO"))
    }

    @Test
    fun `tolera minusculas y espacios alrededor`() {
        assertEquals(EstadoPlato.EN_PREPARACION, EstadoPlato.desdeApi("  en_preparacion  "))
    }

    @Test
    fun `un valor nulo se traduce a desconocido`() {
        assertEquals(EstadoPlato.DESCONOCIDO, EstadoPlato.desdeApi(null))
    }

    @Test
    fun `un valor no reconocido se traduce a desconocido`() {
        assertEquals(EstadoPlato.DESCONOCIDO, EstadoPlato.desdeApi("COCINANDO"))
    }
}
