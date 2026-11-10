package com.lacasablanca.mesero.data.repository

import com.lacasablanca.mesero.data.remote.MesasApi
import com.lacasablanca.mesero.data.remote.dto.CambioEstadoDto
import com.lacasablanca.mesero.data.remote.dto.CuentaResponse
import com.lacasablanca.mesero.data.remote.dto.DividirMesaRequest
import com.lacasablanca.mesero.data.remote.dto.DivisionMesaResponseDto
import com.lacasablanca.mesero.data.remote.dto.FusionarMesasRequest
import com.lacasablanca.mesero.data.remote.dto.MesaDto
import com.lacasablanca.mesero.data.remote.dto.PedidoResponseDto
import com.lacasablanca.mesero.data.remote.dto.TransferirMesaRequest
import com.lacasablanca.mesero.domain.ErrorApp
import com.lacasablanca.mesero.domain.Resultado
import com.lacasablanca.mesero.domain.model.CuentaMesa
import com.lacasablanca.mesero.domain.model.DivisionMesa
import com.lacasablanca.mesero.domain.model.EstadoMesa
import com.lacasablanca.mesero.domain.model.Mesa
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.math.BigDecimal

/** Mapeo DTO -> dominio y traduccion de errores HTTP del repositorio de mesas. */
class MesaRepositoryTest {

    private val api = mockk<MesasApi>()
    private val repositorio = RealMesaRepository(api)

    @Test
    fun `lista convierte los dto al dominio`() = runTest {
        coEvery { api.listar() } returns listOf(
            MesaDto(id = 1, numero = 1, capacidad = 4, ubicacion = "Primer piso", estado = "LIBRE"),
            MesaDto(id = 8, numero = 8, capacidad = 6, ubicacion = "Segundo piso", estado = "CUENTA_SOLICITADA")
        )

        when (val resultado = repositorio.listarMesas()) {
            is Resultado.Exito -> {
                assertEquals(2, resultado.datos.size)
                assertEquals(Mesa(1, 1, 4, "Primer piso", EstadoMesa.LIBRE), resultado.datos[0])
                assertEquals(EstadoMesa.CUENTA_SOLICITADA, resultado.datos[1].estado)
            }

            is Resultado.Fallo -> fail("Se esperaba exito y llego ${resultado.error}")
        }
    }

    @Test
    fun `un estado desconocido no rompe el mapeo`() = runTest {
        coEvery { api.listar() } returns listOf(
            MesaDto(id = 3, numero = 3, capacidad = 4, ubicacion = "Primer piso", estado = "EN_PROCESO"),
            MesaDto(id = 4, numero = 4, capacidad = 4, ubicacion = "Primer piso", estado = null)
        )

        when (val resultado = repositorio.listarMesas()) {
            is Resultado.Exito -> {
                assertEquals(EstadoMesa.DESCONOCIDO, resultado.datos[0].estado)
                assertEquals(EstadoMesa.DESCONOCIDO, resultado.datos[1].estado)
            }

            is Resultado.Fallo -> fail("Se esperaba exito y llego ${resultado.error}")
        }
    }

    @Test
    fun `un 401 se traduce a sesion expirada`() = runTest {
        coEvery { api.listar() } throws errorHttp(401)

        val resultado = repositorio.listarMesas()

        assertTrue(resultado is Resultado.Fallo)
        assertEquals(ErrorApp.SESION_EXPIRADA, (resultado as Resultado.Fallo).error)
    }

    @Test
    fun `un 403 tambien se traduce a sesion expirada`() = runTest {
        coEvery { api.listar() } throws errorHttp(403)

        val resultado = repositorio.listarMesas()

        assertEquals(ErrorApp.SESION_EXPIRADA, (resultado as Resultado.Fallo).error)
    }

    @Test
    fun `un fallo de red se traduce a error de red`() = runTest {
        coEvery { api.listar() } throws IOException("sin red")

        val resultado = repositorio.listarMesas()

        assertEquals(ErrorApp.RED, (resultado as Resultado.Fallo).error)
    }

    @Test
    fun `cambiar estado envia el valor del contrato`() = runTest {
        coEvery {
            api.cambiarEstado(7L, CambioEstadoDto("ATENDIDA"))
        } returns MesaDto(7, 7, 4, "Primer piso", "ATENDIDA")

        when (val resultado = repositorio.cambiarEstado(7L, EstadoMesa.ATENDIDA)) {
            is Resultado.Exito -> assertEquals(EstadoMesa.ATENDIDA, resultado.datos.estado)
            is Resultado.Fallo -> fail("Se esperaba exito y llego ${resultado.error}")
        }

        coVerify(exactly = 1) { api.cambiarEstado(7L, CambioEstadoDto("ATENDIDA")) }
    }

    @Test
    fun `un 409 al abrir la mesa se traduce a conflicto`() = runTest {
        coEvery { api.cambiarEstado(any(), any()) } throws errorHttp(409)

        val resultado = repositorio.cambiarEstado(4L, EstadoMesa.ATENDIDA)

        assertEquals(ErrorApp.CONFLICTO, (resultado as Resultado.Fallo).error)
    }

    @Test
    fun `el repositorio fake conserva el cambio de estado`() = runTest {
        val repositorio = FakeMesaRepository(latenciaMs = 0)
        val libre = (repositorio.listarMesas() as Resultado.Exito).datos.first { it.estaLibre }

        val cambio = repositorio.cambiarEstado(libre.id, EstadoMesa.ATENDIDA)
        assertTrue(cambio is Resultado.Exito)

        val despues = (repositorio.listarMesas() as Resultado.Exito).datos.first { it.id == libre.id }
        assertEquals(EstadoMesa.ATENDIDA, despues.estado)
    }

    @Test
    fun `dividir envia item_ids y mesa destino y mapea la respuesta`() = runTest {
        coEvery {
            api.dividirMesa(3L, DividirMesaRequest(listOf(501L, 502L), 8L))
        } returns DivisionMesaResponseDto(
            mesaOrigenId = 3L,
            mesaDestinoId = 8L,
            pedidoOrigen = PedidoResponseDto(id = 100L),
            pedidoNuevo = PedidoResponseDto(id = 200L),
            totalCombinado = BigDecimal("120.50")
        )

        when (val resultado = repositorio.dividirMesa(3L, listOf(501L, 502L), 8L)) {
            is Resultado.Exito -> assertEquals(
                DivisionMesa(3L, 8L, 100L, 200L, BigDecimal("120.50")),
                resultado.datos
            )

            is Resultado.Fallo -> fail("Se esperaba exito y llego ${resultado.error}")
        }

        coVerify(exactly = 1) {
            api.dividirMesa(3L, DividirMesaRequest(listOf(501L, 502L), 8L))
        }
    }

    @Test
    fun `dividir sin item_ids no llama a la api`() = runTest {
        val resultado = repositorio.dividirMesa(3L, emptyList(), 8L)

        assertEquals(ErrorApp.VALIDACION, (resultado as Resultado.Fallo).error)
        coVerify(exactly = 0) { api.dividirMesa(any(), any()) }
    }

    @Test
    fun `fusionar usa la mesa destino como ruta y la origen en el cuerpo`() = runTest {
        coEvery {
            api.fusionarMesa(8L, FusionarMesasRequest(mesaOrigenId = 3L))
        } returns CuentaResponse(mesaId = 8L, total = BigDecimal("96.00"))

        when (val resultado = repositorio.fusionarMesas(8L, 3L)) {
            is Resultado.Exito -> assertEquals(
                CuentaMesa(mesaId = 8L, total = BigDecimal("96.00")),
                resultado.datos
            )

            is Resultado.Fallo -> fail("Se esperaba exito y llego ${resultado.error}")
        }

        coVerify(exactly = 1) { api.fusionarMesa(8L, FusionarMesasRequest(3L)) }
    }

    @Test
    fun `transferir envia la mesa destino y mapea la cuenta`() = runTest {
        coEvery {
            api.transferirMesa(3L, TransferirMesaRequest(mesaDestinoId = 8L))
        } returns CuentaResponse(mesaId = 8L, total = BigDecimal("48.00"))

        when (val resultado = repositorio.transferirMesa(3L, 8L)) {
            is Resultado.Exito -> assertEquals(
                CuentaMesa(mesaId = 8L, total = BigDecimal("48.00")),
                resultado.datos
            )

            is Resultado.Fallo -> fail("Se esperaba exito y llego ${resultado.error}")
        }
    }

    @Test
    fun `un 409 en transferir se traduce a conflicto`() = runTest {
        coEvery { api.transferirMesa(any(), any()) } throws errorHttp(409)

        val resultado = repositorio.transferirMesa(3L, 9L)

        assertEquals(ErrorApp.CONFLICTO, (resultado as Resultado.Fallo).error)
    }

    private fun errorHttp(codigo: Int): HttpException {
        val cuerpo = "{\"mensaje\":\"error\"}".toResponseBody("application/json".toMediaType())
        return HttpException(Response.error<Any>(codigo, cuerpo))
    }
}
