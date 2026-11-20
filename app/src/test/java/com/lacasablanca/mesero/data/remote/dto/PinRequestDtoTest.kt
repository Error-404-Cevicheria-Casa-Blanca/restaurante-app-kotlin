package com.lacasablanca.mesero.data.remote.dto

import com.google.gson.Gson
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Serializacion del PIN de login con el codigo de terminal (PRB-101). */
class PinRequestDtoTest {

    private val gson = Gson()

    @Test
    fun `serializa terminal_codigo en snake_case`() {
        val json = gson.toJson(
            PinRequestDto(usuario = "mozo_01", pin = "1234", terminalCodigo = "TERM-01")
        )

        assertTrue(json.contains("\"usuario\":\"mozo_01\""))
        assertTrue(json.contains("\"pin\":\"1234\""))
        assertTrue(json.contains("\"terminal_codigo\":\"TERM-01\""))
        assertFalse(json.contains("terminalCodigo"))
    }

    @Test
    fun `omite terminal_codigo cuando es nulo`() {
        val json = gson.toJson(PinRequestDto(usuario = "mozo_01", pin = "1234"))

        assertFalse(json.contains("terminal_codigo"))
    }
}
