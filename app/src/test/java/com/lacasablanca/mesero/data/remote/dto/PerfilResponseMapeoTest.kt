package com.lacasablanca.mesero.data.remote.dto

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mapeo de GET /api/v1/auth/me (PRB-102). */
class PerfilResponseMapeoTest {

    private val gson = Gson()

    @Test
    fun `mapea usuario turno terminal y fecha del servidor`() {
        val json = """
            {
              "usuario_id": "1",
              "roles": [{"authority": "ROLE_MOZO"}],
              "usuario": {
                "id": 1,
                "nombre": "mozo_01",
                "nombre_visible": "David Rojas",
                "rol": "MOZO",
                "turno": {"id": 2, "nombre": "Turno Noche", "inicio": "18:00", "fin": "23:00"},
                "terminal": {"id": 2, "codigo": "TERM-02", "nombre": "Tablet Barra"}
              },
              "turno": {"id": 2, "nombre": "Turno Noche", "inicio": "18:00", "fin": "23:00"},
              "terminal": {"id": 2, "codigo": "TERM-02", "nombre": "Tablet Barra"},
              "fecha_hora_servidor": "2026-11-20T18:30:00-05:00"
            }
        """.trimIndent()

        val dto = gson.fromJson(json, PerfilResponseDto::class.java)

        val usuario = dto.usuario
        assertNotNull(usuario)
        assertEquals(1L, usuario?.id)
        assertEquals("mozo_01", usuario?.nombre)
        assertEquals("David Rojas", usuario?.nombreVisible)
        assertEquals("MOZO", usuario?.rol)
        assertEquals("Turno Noche", usuario?.turno?.nombre)
        assertEquals("18:00", usuario?.turno?.inicio)
        assertEquals("TERM-02", usuario?.terminal?.codigo)
        assertEquals("2026-11-20T18:30:00-05:00", dto.fechaHoraServidor)
    }

    @Test
    fun `serializa la fecha del servidor como fecha_hora_servidor`() {
        val dto = PerfilResponseDto(
            usuario = UsuarioDto(
                id = 1L,
                nombre = "mozo_01",
                nombreVisible = "David Rojas",
                rol = "MOZO"
            ),
            fechaHoraServidor = "2026-11-20T18:30:00-05:00"
        )

        val json = gson.toJson(dto)

        assertTrue(json.contains("\"fecha_hora_servidor\""))
        assertTrue(json.contains("\"nombre_visible\""))
        assertTrue(json.contains("2026-11-20T18:30:00-05:00"))
    }
}
