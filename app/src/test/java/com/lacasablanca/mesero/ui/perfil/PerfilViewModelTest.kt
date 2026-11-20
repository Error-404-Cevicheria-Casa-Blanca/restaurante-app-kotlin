package com.lacasablanca.mesero.ui.perfil

import com.lacasablanca.mesero.data.repository.AuthRepository
import com.lacasablanca.mesero.data.repository.PerfilActualizado
import com.lacasablanca.mesero.domain.ErrorApp
import com.lacasablanca.mesero.domain.Resultado
import com.lacasablanca.mesero.domain.model.Sesion
import com.lacasablanca.mesero.domain.model.Terminal
import com.lacasablanca.mesero.domain.model.Turno
import com.lacasablanca.mesero.domain.model.Usuario
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

    private fun sesionLocal(nombreVisible: String = "Mozo Local"): Sesion = Sesion(
        token = "token-prueba",
        usuario = Usuario(
            id = 1L,
            nombre = "mozo_01",
            rol = "MOZO",
            nombreVisible = nombreVisible,
            turno = Turno(nombre = "Turno Tarde"),
            terminal = Terminal(codigo = "TERM-01", nombre = "Tablet Salón Principal")
        )
    )

    @Test
    fun `con turno y terminal llena nombre visible y asignaciones`() = runTest(testDispatcher) {
        val sesion = Sesion(
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
        coEvery { authRepository.sesionGuardada() } returns sesion
        coEvery { authRepository.refrescarPerfil() } returns
            Resultado.Exito(PerfilActualizado(sesion, null))

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
        val sesion = Sesion(
            token = "token-prueba",
            usuario = Usuario(id = 2L, nombre = "mozo_02", rol = "MOZO")
        )
        coEvery { authRepository.sesionGuardada() } returns sesion
        coEvery { authRepository.refrescarPerfil() } returns
            Resultado.Exito(PerfilActualizado(sesion, null))

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

    @Test
    fun `el refresco exitoso usa los datos y la fecha del servidor`() = runTest(testDispatcher) {
        val local = sesionLocal()
        val remota = Sesion(
            token = "token-prueba",
            usuario = Usuario(
                id = 1L,
                nombre = "mozo_01",
                rol = "MOZO",
                nombreVisible = "David Rojas",
                turno = Turno(nombre = "Turno Noche"),
                terminal = Terminal(codigo = "TERM-02", nombre = "Tablet Barra")
            )
        )
        coEvery { authRepository.sesionGuardada() } returns local
        coEvery { authRepository.refrescarPerfil() } returns Resultado.Exito(
            PerfilActualizado(remota, "2026-11-21T19:45:00-05:00")
        )

        val viewModel = PerfilViewModel(authRepository, testDispatcher) { fechaFija }
        advanceUntilIdle()

        val estado = viewModel.estado.value
        assertEquals("David Rojas", estado.nombre)
        assertEquals("Turno Noche", estado.turno)
        assertEquals("Tablet Barra", estado.terminal)
        assertEquals("21/11/2026", estado.fecha)
        assertFalse(estado.actualizando)
        assertFalse(estado.avisoRed)
    }

    @Test
    fun `un fallo de red conserva la sesion local y avisa`() = runTest(testDispatcher) {
        coEvery { authRepository.sesionGuardada() } returns sesionLocal("Mozo Local")
        coEvery { authRepository.refrescarPerfil() } returns
            Resultado.Fallo(ErrorApp.RED)

        val viewModel = PerfilViewModel(authRepository, testDispatcher) { fechaFija }
        advanceUntilIdle()

        val estado = viewModel.estado.value
        assertEquals("Mozo Local", estado.nombre)
        assertEquals("Turno Tarde", estado.turno)
        assertEquals("18/11/2026", estado.fecha)
        assertTrue(estado.avisoRed)
        assertFalse(estado.actualizando)
    }

    @Test
    fun `un 401 al refrescar navega a login`() = runTest(testDispatcher) {
        coEvery { authRepository.sesionGuardada() } returns sesionLocal()
        coEvery { authRepository.refrescarPerfil() } returns
            Resultado.Fallo(ErrorApp.SESION_EXPIRADA)
        coEvery { authRepository.cerrarSesion() } returns Unit

        val viewModel = PerfilViewModel(authRepository, testDispatcher) { fechaFija }
        val eventos = escucharEventos(viewModel)
        advanceUntilIdle()

        assertTrue(eventos.contains(PerfilEvento.IrALogin))
        assertFalse(viewModel.estado.value.actualizando)
    }

    @Test
    fun `un 401 cierra la sesion y emite IrALogin`() = runTest(testDispatcher) {
        coEvery { authRepository.sesionGuardada() } returns sesionLocal()
        coEvery { authRepository.refrescarPerfil() } returns
            Resultado.Fallo(ErrorApp.SESION_EXPIRADA)
        coEvery { authRepository.cerrarSesion() } returns Unit

        val viewModel = PerfilViewModel(authRepository, testDispatcher) { fechaFija }
        val eventos = escucharEventos(viewModel)
        advanceUntilIdle()

        coVerify(exactly = 1) { authRepository.cerrarSesion() }
        assertTrue(eventos.contains(PerfilEvento.IrALogin))
        assertFalse(viewModel.estado.value.actualizando)
    }
}
