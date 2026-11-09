package com.lacasablanca.mesero.data.repository

import com.lacasablanca.mesero.data.local.offline.MesaDao
import com.lacasablanca.mesero.data.local.offline.MesaEntity
import com.lacasablanca.mesero.domain.ErrorApp
import com.lacasablanca.mesero.domain.Resultado
import com.lacasablanca.mesero.domain.model.EstadoMesa
import com.lacasablanca.mesero.domain.model.Mesa
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** PRB-62: cache del mapa de mesas y actualizacion tras PATCH exitoso. */
class MesaOfflineRepositoryTest {

    private val remoto = mockk<MesaRepository>()
    private val dao = mockk<MesaDao>()
    private val repositorio = MesaOfflineRepository(remoto, dao)

    private val mesa = Mesa(1L, 1, 4, "Primer piso", EstadoMesa.OCUPADA)
    private val mesaEntity = MesaEntity(1L, 1, 4, "Primer piso", "OCUPADA")

    @Test
    fun `con red devuelve las mesas y actualiza la cache`() = runTest {
        coEvery { remoto.listarMesas() } returns Resultado.Exito(listOf(mesa))
        coEvery { dao.reemplazar(any()) } just Runs

        val resultado = repositorio.listarMesas()

        assertEquals(1L, (resultado as Resultado.Exito).datos.single().id)
        coVerify(exactly = 1) { dao.reemplazar(match { it.single().estado == "OCUPADA" }) }
    }

    @Test
    fun `sin red devuelve las mesas cacheadas`() = runTest {
        coEvery { remoto.listarMesas() } returns Resultado.Fallo(ErrorApp.RED)
        coEvery { dao.mesas() } returns listOf(mesaEntity)

        val resultado = repositorio.listarMesas()

        assertTrue(resultado is Resultado.Exito)
        assertEquals(EstadoMesa.OCUPADA, (resultado as Resultado.Exito).datos.single().estado)
    }

    @Test
    fun `sin red y sin cache conserva el error`() = runTest {
        coEvery { remoto.listarMesas() } returns Resultado.Fallo(ErrorApp.RED)
        coEvery { dao.mesas() } returns emptyList()

        val resultado = repositorio.listarMesas()

        assertEquals(ErrorApp.RED, (resultado as Resultado.Fallo).error)
    }

    @Test
    fun `un cambio de estado exitoso actualiza la cache`() = runTest {
        val libre = mesa.copy(estado = EstadoMesa.LIBRE)
        coEvery { remoto.cambiarEstado(1L, EstadoMesa.LIBRE) } returns Resultado.Exito(libre)
        coEvery { dao.actualizarEstado(1L, "LIBRE") } just Runs

        val resultado = repositorio.cambiarEstado(1L, EstadoMesa.LIBRE)

        assertTrue(resultado is Resultado.Exito)
        coVerify(exactly = 1) { dao.actualizarEstado(1L, "LIBRE") }
    }

    @Test
    fun `un cambio de estado sin red no escribe la cache`() = runTest {
        coEvery { remoto.cambiarEstado(1L, EstadoMesa.LIBRE) } returns
            Resultado.Fallo(ErrorApp.RED)

        val resultado = repositorio.cambiarEstado(1L, EstadoMesa.LIBRE)

        assertEquals(ErrorApp.RED, (resultado as Resultado.Fallo).error)
        coVerify(exactly = 0) { dao.actualizarEstado(any(), any()) }
    }
}
