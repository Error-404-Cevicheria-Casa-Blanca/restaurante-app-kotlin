package com.lacasablanca.mesero.ui.comanda

import com.lacasablanca.mesero.data.repository.MesaRepository
import com.lacasablanca.mesero.domain.ErrorApp
import com.lacasablanca.mesero.domain.Resultado
import com.lacasablanca.mesero.domain.model.EstadoMesa
import com.lacasablanca.mesero.domain.model.Mesa
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ComandaViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val mesaRepository = mockk<MesaRepository>()

    private val atendida = Mesa(2, 2, 2, "Primer piso", EstadoMesa.ATENDIDA)
    private val libre = atendida.copy(estado = EstadoMesa.LIBRE)

    @Before
    fun preparar() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun limpiar() {
        Dispatchers.resetMain()
    }

    private fun TestScope.escucharEventos(viewModel: ComandaViewModel): List<ComandaEvento> {
        val eventos = mutableListOf<ComandaEvento>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.eventos.toList(eventos)
        }
        return eventos
    }

    @Test
    fun `liberar la mesa hace patch a libre y avisa que se cerro`() = runTest(testDispatcher) {
        coEvery { mesaRepository.cambiarEstado(atendida.id, EstadoMesa.LIBRE) } returns
            Resultado.Exito(libre)

        val viewModel = ComandaViewModel(mesaRepository, testDispatcher)
        val eventos = escucharEventos(viewModel)

        viewModel.liberarMesa(atendida.id)
        advanceUntilIdle()

        coVerify(exactly = 1) { mesaRepository.cambiarEstado(atendida.id, EstadoMesa.LIBRE) }
        assertTrue(eventos.any { it is ComandaEvento.MesaLiberada })
        assertFalse(viewModel.estado.value.liberandoMesa)
    }

    @Test
    fun `si el patch falla por red avisa y deja reintentar`() = runTest(testDispatcher) {
        coEvery { mesaRepository.cambiarEstado(atendida.id, EstadoMesa.LIBRE) } returns
            Resultado.Fallo(ErrorApp.RED)

        val viewModel = ComandaViewModel(mesaRepository, testDispatcher)
        val eventos = escucharEventos(viewModel)

        viewModel.liberarMesa(atendida.id)
        advanceUntilIdle()

        val error = eventos.filterIsInstance<ComandaEvento.ErrorAlLiberar>().single()
        assertEquals(ErrorApp.RED, error.error)
        assertTrue(eventos.none { it is ComandaEvento.MesaLiberada })
        assertFalse(viewModel.estado.value.liberandoMesa)
    }

    @Test
    fun `si el patch devuelve 401 avisa sesion expirada`() = runTest(testDispatcher) {
        coEvery { mesaRepository.cambiarEstado(atendida.id, EstadoMesa.LIBRE) } returns
            Resultado.Fallo(ErrorApp.SESION_EXPIRADA)

        val viewModel = ComandaViewModel(mesaRepository, testDispatcher)
        val eventos = escucharEventos(viewModel)

        viewModel.liberarMesa(atendida.id)
        advanceUntilIdle()

        assertTrue(eventos.any { it is ComandaEvento.SesionExpirada })
        assertTrue(eventos.none { it is ComandaEvento.ErrorAlLiberar })
    }

    @Test
    fun `un id invalido no llama al repositorio`() = runTest(testDispatcher) {
        val viewModel = ComandaViewModel(mesaRepository, testDispatcher)
        val eventos = escucharEventos(viewModel)

        viewModel.liberarMesa(0L)
        advanceUntilIdle()

        coVerify(exactly = 0) { mesaRepository.cambiarEstado(any(), any()) }
        assertTrue(eventos.isEmpty())
    }

    @Test
    fun `los toques repetidos no duplican el patch`() = runTest(testDispatcher) {
        coEvery { mesaRepository.cambiarEstado(atendida.id, EstadoMesa.LIBRE) } returns
            Resultado.Exito(libre)

        val viewModel = ComandaViewModel(mesaRepository, testDispatcher)
        escucharEventos(viewModel)

        viewModel.liberarMesa(atendida.id)
        viewModel.liberarMesa(atendida.id)
        advanceUntilIdle()

        coVerify(exactly = 1) { mesaRepository.cambiarEstado(atendida.id, EstadoMesa.LIBRE) }
    }

    // ---------------------------------------------------------------------
    // Pedir cuenta (PRB-83)
    // ---------------------------------------------------------------------

    @Test
    fun `pedir cuenta hace patch a cuenta solicitada y avisa`() = runTest(testDispatcher) {
        val enCuenta = atendida.copy(estado = EstadoMesa.CUENTA_SOLICITADA)
        coEvery {
            mesaRepository.cambiarEstado(atendida.id, EstadoMesa.CUENTA_SOLICITADA)
        } returns Resultado.Exito(enCuenta)

        val viewModel = ComandaViewModel(mesaRepository, testDispatcher)
        val eventos = escucharEventos(viewModel)

        viewModel.pedirCuenta(atendida.id)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            mesaRepository.cambiarEstado(atendida.id, EstadoMesa.CUENTA_SOLICITADA)
        }
        assertTrue(eventos.any { it is ComandaEvento.CuentaSolicitada })
        assertFalse(viewModel.estado.value.solicitandoCuenta)
    }

    @Test
    fun `si el patch de la cuenta falla por red avisa y deja reintentar`() =
        runTest(testDispatcher) {
            coEvery {
                mesaRepository.cambiarEstado(atendida.id, EstadoMesa.CUENTA_SOLICITADA)
            } returns Resultado.Fallo(ErrorApp.RED)

            val viewModel = ComandaViewModel(mesaRepository, testDispatcher)
            val eventos = escucharEventos(viewModel)

            viewModel.pedirCuenta(atendida.id)
            advanceUntilIdle()

            val error = eventos.filterIsInstance<ComandaEvento.ErrorAlPedirCuenta>().single()
            assertEquals(ErrorApp.RED, error.error)
            assertTrue(eventos.none { it is ComandaEvento.CuentaSolicitada })
            assertFalse(viewModel.estado.value.solicitandoCuenta)
        }

    @Test
    fun `un 409 al pedir la cuenta avisa conflicto`() = runTest(testDispatcher) {
        coEvery {
            mesaRepository.cambiarEstado(atendida.id, EstadoMesa.CUENTA_SOLICITADA)
        } returns Resultado.Fallo(ErrorApp.CONFLICTO)

        val viewModel = ComandaViewModel(mesaRepository, testDispatcher)
        val eventos = escucharEventos(viewModel)

        viewModel.pedirCuenta(atendida.id)
        advanceUntilIdle()

        val error = eventos.filterIsInstance<ComandaEvento.ErrorAlPedirCuenta>().single()
        assertEquals(ErrorApp.CONFLICTO, error.error)
    }

    @Test
    fun `si el patch de la cuenta devuelve 401 avisa sesion expirada`() =
        runTest(testDispatcher) {
            coEvery {
                mesaRepository.cambiarEstado(atendida.id, EstadoMesa.CUENTA_SOLICITADA)
            } returns Resultado.Fallo(ErrorApp.SESION_EXPIRADA)

            val viewModel = ComandaViewModel(mesaRepository, testDispatcher)
            val eventos = escucharEventos(viewModel)

            viewModel.pedirCuenta(atendida.id)
            advanceUntilIdle()

            assertTrue(eventos.any { it is ComandaEvento.SesionExpirada })
            assertTrue(eventos.none { it is ComandaEvento.ErrorAlPedirCuenta })
        }

    @Test
    fun `un id invalido no pide la cuenta`() = runTest(testDispatcher) {
        val viewModel = ComandaViewModel(mesaRepository, testDispatcher)
        val eventos = escucharEventos(viewModel)

        viewModel.pedirCuenta(0L)
        advanceUntilIdle()

        coVerify(exactly = 0) { mesaRepository.cambiarEstado(any(), any()) }
        assertTrue(eventos.isEmpty())
    }

    @Test
    fun `los toques repetidos no duplican el patch de la cuenta`() = runTest(testDispatcher) {
        coEvery {
            mesaRepository.cambiarEstado(atendida.id, EstadoMesa.CUENTA_SOLICITADA)
        } returns Resultado.Exito(atendida.copy(estado = EstadoMesa.CUENTA_SOLICITADA))

        val viewModel = ComandaViewModel(mesaRepository, testDispatcher)
        escucharEventos(viewModel)

        viewModel.pedirCuenta(atendida.id)
        viewModel.pedirCuenta(atendida.id)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            mesaRepository.cambiarEstado(atendida.id, EstadoMesa.CUENTA_SOLICITADA)
        }
    }
}
