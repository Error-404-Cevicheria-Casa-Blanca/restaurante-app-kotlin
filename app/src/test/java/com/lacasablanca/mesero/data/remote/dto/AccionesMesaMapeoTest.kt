package com.lacasablanca.mesero.data.remote.dto

import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.math.BigDecimal

/** PRB-93: contrato real de dividir, fusionar y transferir (PRB-75). */
class AccionesMesaMapeoTest {

    @Test
    fun `dividir serializa item_ids y mesa_destino_id`() {
        val json = JsonParser.parseString(
            Gson().toJson(
                DividirMesaRequest(itemIds = listOf(501L, 502L), mesaDestinoId = 8L)
            )
        ).asJsonObject

        assertEquals(2, json.getAsJsonArray("item_ids").size())
        assertEquals(501L, json.getAsJsonArray("item_ids")[0].asLong)
        assertEquals(8L, json.get("mesa_destino_id").asLong)
        assertFalse(json.has("mesaDestinoId"))
    }

    @Test
    fun `fusionar envia la mesa origen y transferir la destino`() {
        val fusionar = JsonParser.parseString(
            Gson().toJson(FusionarMesasRequest(mesaOrigenId = 3L))
        ).asJsonObject
        val transferir = JsonParser.parseString(
            Gson().toJson(TransferirMesaRequest(mesaDestinoId = 8L))
        ).asJsonObject

        assertEquals(3L, fusionar.get("mesa_origen_id").asLong)
        assertEquals(8L, transferir.get("mesa_destino_id").asLong)
        assertFalse(fusionar.has("mesaOrigenId"))
        assertFalse(transferir.has("mesaDestinoId"))
    }

    @Test
    fun `mapea la respuesta real de division con ids y total`() {
        val respuesta = Gson().fromJson(
            """
            {
              "mesa_origen_id": 3,
              "mesa_destino_id": 8,
              "pedido_origen": {"id": 100, "estado": "PENDIENTE"},
              "pedido_nuevo": {"id": 200, "estado": "PENDIENTE"},
              "total_combinado": 120.5
            }
            """.trimIndent(),
            DivisionMesaResponseDto::class.java
        ).aDominio()

        assertEquals(3L, respuesta.mesaOrigenId)
        assertEquals(8L, respuesta.mesaDestinoId)
        assertEquals(100L, respuesta.pedidoOrigenId)
        assertEquals(200L, respuesta.pedidoNuevoId)
        assertEquals(BigDecimal("120.50"), respuesta.totalCombinado)
    }

    @Test
    fun `una division sin ids ni total no rompe el mapeo`() {
        val respuesta = DivisionMesaResponseDto().aDominio()

        assertEquals(0L, respuesta.mesaOrigenId)
        assertEquals(0L, respuesta.mesaDestinoId)
        assertEquals(0L, respuesta.pedidoOrigenId)
        assertEquals(0L, respuesta.pedidoNuevoId)
        assertEquals(BigDecimal("0.00"), respuesta.totalCombinado)
    }

    @Test
    fun `fusionar y transferir mapean la cuenta devuelta`() {
        val cuenta = CuentaResponse(
            mesaId = 8L,
            total = BigDecimal("96")
        ).aCuentaMesa(mesaIdPorDefecto = 8L)

        assertEquals(8L, cuenta.mesaId)
        assertEquals(BigDecimal("96.00"), cuenta.total)
    }

    @Test
    fun `una cuenta sin mesa usa el id de la peticion y total cero`() {
        val cuenta = CuentaResponse().aCuentaMesa(mesaIdPorDefecto = 5L)

        assertEquals(5L, cuenta.mesaId)
        assertEquals(BigDecimal("0.00"), cuenta.total)
    }
}
