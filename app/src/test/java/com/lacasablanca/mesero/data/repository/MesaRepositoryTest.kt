package com.lacasablanca.mesero.data.repository

import com.lacasablanca.mesero.data.remote.MesasApi
import com.lacasablanca.mesero.data.remote.dto.CambioEstadoDto
import com.lacasablanca.mesero.data.remote.dto.MesaDto
import com.lacasablanca.mesero.domain.ErrorApp
import com.lacasablanca.mesero.domain.Resultado
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

    private fun errorHttp(codigo: Int): HttpException {
        val cuerpo = "{\"mensaje\":\"error\"}".toResponseBody("application/json".toMediaType())
        return HttpException(Response.error<Any>(codigo, cuerpo))
    }
}
