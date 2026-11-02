package com.lacasablanca.mesero.ui.mesas

import com.lacasablanca.mesero.data.repository.AuthRepository
import com.lacasablanca.mesero.data.repository.FakeMesaRepository
import com.lacasablanca.mesero.data.repository.MesaRepository
import com.lacasablanca.mesero.domain.ErrorApp
import com.lacasablanca.mesero.domain.Resultado
import com.lacasablanca.mesero.domain.model.EstadoMesa
import com.lacasablanca.mesero.domain.model.Mesa
import com.lacasablanca.mesero.domain.model.Sesion
import com.lacasablanca.mesero.domain.model.Usuario
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * PRB-42: refresco automatico del mapa cada 4 segundos.
 *
 * El reloj virtual del [StandardTestDispatcher] permite comprobar la carga
 * inmediata, el segundo ciclo, la cancelacion y la tolerancia a errores sin
 * esperas reales de cuatro segundos. La unica carga inicial es la del bucle:
 * el ViewModel no pide mesas al construirse.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MesasViewModelPollingTest {

    private val testDispatcher = StandardTestDispatcher()

    private val mesaRepository = mockk<MesaRepository>()
    private val authRepository = mockk<AuthRepository>(relaxed = true)

    private val mesas = FakeMesaRepository.MESAS_FAKE

    @Before
    fun preparar() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun limpiar() {
        Dispatchers.resetMain()
    }

    private fun crearViewModel(): MesasViewModel {
        coEvery { authRepository.sesionGuardada() } returns Sesion(
            token = "token-prueba",
            usuario = Usuario(id = 1, nombre = "Mozo Prueba", rol = "MOZO")
        )
        return MesasViewModel(mesaRepository, authRepository, testDispatcher)
    }

    private fun TestScope.escucharErrores(viewModel: MesasViewModel): List<String> {
        val errores = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.eventosError.collect { errores.add(it) }
        }
        return errores
    }

    private fun MesasViewModel.estadoDe(mesa: Mesa): EstadoMesa? =
        estado.value.mesas.firstOrNull { it.id == mesa.id }?.estado

    @Test
    fun `el polling hace la carga inmediata al arrancar`() = runTest(testDispatcher) {
        coEvery { mesaRepository.listarMesas() } returns Resultado.Exito(mesas)

        val viewModel = crearViewModel()
        runCurrent()
        // El arranque del ViewModel ya no pide mesas: solo resuelve la sesion.
        assertTrue(viewModel.estado.value.mesas.isEmpty())
        coVerify(exactly = 0) { mesaRepository.listarMesas() }

        viewModel.startPolling()
        runCurrent()

        // Sin avanzar el reloj, el primer ciclo del bucle ya trajo las mesas.
        assertEquals(mesas.size, viewModel.estado.value.mesas.size)
        coVerify(exactly = 1) { mesaRepository.listarMesas() }
        viewModel.stopPolling()
    }

    @Test
    fun `el segundo ciclo llega a los 4 segundos`() = runTest(testDispatcher) {
        val libre = mesas.first { it.estaLibre }
        val conCambio = mesas.map {
            if (it.id == libre.id) it.copy(estado = EstadoMesa.ATENDIDA) else it
        }
        coEvery { mesaRepository.listarMesas() } returnsMany listOf(
            Resultado.Exito(mesas),
            Resultado.Exito(conCambio)
        )

        val viewModel = crearViewModel()
        runCurrent()
        viewModel.startPolling()
        runCurrent()
        assertEquals(EstadoMesa.LIBRE, viewModel.estadoDe(libre))

        advanceTimeBy(MesasViewModel.INTERVALO_POLLING_MS - 1)
        runCurrent()
        coVerify(exactly = 1) { mesaRepository.listarMesas() }

        advanceTimeBy(1)
        runCurrent()
        coVerify(exactly = 2) { mesaRepository.listarMesas() }
        assertEquals(EstadoMesa.ATENDIDA, viewModel.estadoDe(libre))
        viewModel.stopPolling()
    }

    @Test
    fun `stopPolling cancela y no vuelve a consultar`() = runTest(testDispatcher) {
        coEvery { mesaRepository.listarMesas() } returns Resultado.Exito(mesas)

        val viewModel = crearViewModel()
        runCurrent()
        viewModel.startPolling()
        runCurrent()
        coVerify(exactly = 1) { mesaRepository.listarMesas() }

        viewModel.stopPolling()
        advanceTimeBy(MesasViewModel.INTERVALO_POLLING_MS * 3)
        runCurrent()

        coVerify(exactly = 1) { mesaRepository.listarMesas() }
    }

    @Test
    fun `si el polling falla por red conserva la lista y avisa una vez`() = runTest(testDispatcher) {
        coEvery { mesaRepository.listarMesas() } returnsMany listOf(
            Resultado.Exito(mesas),
            Resultado.Fallo(ErrorApp.RED),
            Resultado.Fallo(ErrorApp.RED)
        )

        val viewModel = crearViewModel()
        val errores = escucharErrores(viewModel)
        runCurrent()
        viewModel.startPolling()
        runCurrent()
        assertEquals(mesas.size, viewModel.estado.value.mesas.size)

        advanceTimeBy(MesasViewModel.INTERVALO_POLLING_MS)
        runCurrent()

        // La lista ya pintada se conserva: el fallo silencioso no rompe la pantalla.
        assertEquals(mesas.size, viewModel.estado.value.mesas.size)
        assertNull(viewModel.estado.value.error)
        assertEquals(listOf(MesasViewModel.ERROR_RED), errores)

        // Un segundo fallo seguido no repite el Snackbar.
        advanceTimeBy(MesasViewModel.INTERVALO_POLLING_MS)
        runCurrent()
        assertEquals(listOf(MesasViewModel.ERROR_RED), errores)
        coVerify(exactly = 3) { mesaRepository.listarMesas() }
        viewModel.stopPolling()
    }

    @Test
    fun `llamar dos veces a startPolling no duplica el Job`() = runTest(testDispatcher) {
        coEvery { mesaRepository.listarMesas() } returns Resultado.Exito(mesas)

        val viewModel = crearViewModel()
        runCurrent()
        coVerify(exactly = 0) { mesaRepository.listarMesas() }

        viewModel.startPolling()
        viewModel.startPolling()
        runCurrent()
        coVerify(exactly = 1) { mesaRepository.listarMesas() }

        advanceTimeBy(MesasViewModel.INTERVALO_POLLING_MS)
        runCurrent()
        // Un solo bucle: una carga mas por ciclo, no dos.
        coVerify(exactly = 2) { mesaRepository.listarMesas() }
        assertTrue(viewModel.estado.value.mesas.isNotEmpty())
        viewModel.stopPolling()
    }
}
