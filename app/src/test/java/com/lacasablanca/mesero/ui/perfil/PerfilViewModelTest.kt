package com.lacasablanca.mesero.ui.perfil

import com.lacasablanca.mesero.data.repository.AuthRepository
import com.lacasablanca.mesero.domain.model.Sesion
import com.lacasablanca.mesero.domain.model.Terminal
import com.lacasablanca.mesero.domain.model.Turno
import com.lacasablanca.mesero.domain.model.Usuario
import io.mockk.coEvery
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
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class PerfilViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val authRepository = mockk<AuthRepository>()
    private val fechaFija = LocalDate.of(2026, 11, 18)

    @Before
    fun preparar() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun limpiar() {
        Dispatchers.resetMain()
    }

    private fun TestScope.escucharEventos(viewModel: PerfilViewModel): List<PerfilEvento> {
        val eventos = mutableListOf<PerfilEvento>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.eventos.toList(eventos)
        }
        return eventos
    }

    @Test
    fun `con turno y terminal llena nombre visible y asignaciones`() = runTest(testDispatcher) {
        coEvery { authRepository.sesionGuardada() } returns Sesion(
            token = "token-prueba",
            usuario = Usuario(
                id = 1L,
                nombre = "mozo_01",
                rol = "MOZO",
                nombreVisible = "David Rojas",
                turno = Turno(nombre = "Turno Tarde"),
                terminal = Terminal(codigo = "TERM-01", nombre = "Tablet Salón Principal")
            )
        )

        val viewModel = PerfilViewModel(authRepository, testDispatcher, fechaActual = { fechaFija })
        advanceUntilIdle()

        val estado = viewModel.estado.value
        assertFalse(estado.cargando)
        assertEquals("David Rojas", estado.nombre)
        assertEquals("mozo_01", estado.usuario)
        assertEquals("Turno Tarde", estado.turno)
        assertEquals("Tablet Salón Principal", estado.terminal)
        assertEquals("18/11/2026", estado.fecha)
    }

    @Test
    fun `sin turno ni terminal deja los campos vacios`() = runTest(testDispatcher) {
        coEvery { authRepository.sesionGuardada() } returns Sesion(
            token = "token-prueba",
            usuario = Usuario(id = 2L, nombre = "mozo_02", rol = "MOZO")
        )

        val viewModel = PerfilViewModel(authRepository, testDispatcher, fechaActual = { fechaFija })
        advanceUntilIdle()

        val estado = viewModel.estado.value
        assertEquals("mozo_02", estado.nombre)
        assertEquals("", estado.turno)
        assertEquals("", estado.terminal)
    }

    @Test
    fun `sin sesion emite IrALogin y deja de cargar`() = runTest(testDispatcher) {
        coEvery { authRepository.sesionGuardada() } returns null

        val viewModel = PerfilViewModel(authRepository, testDispatcher, fechaActual = { fechaFija })
        val eventos = escucharEventos(viewModel)
        advanceUntilIdle()

        assertTrue(eventos.contains(PerfilEvento.IrALogin))
        assertFalse(viewModel.estado.value.cargando)
    }
}
