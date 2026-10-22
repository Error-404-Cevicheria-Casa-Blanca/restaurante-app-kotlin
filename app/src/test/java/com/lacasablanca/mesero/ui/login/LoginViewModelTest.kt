package com.lacasablanca.mesero.ui.login

import com.lacasablanca.mesero.data.repository.AuthRepository
import com.lacasablanca.mesero.domain.ErrorApp
import com.lacasablanca.mesero.domain.Resultado
import com.lacasablanca.mesero.domain.model.Sesion
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

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val authRepository = mockk<AuthRepository>()

    private val sesion = Sesion(
        token = "token-prueba",
        usuario = Usuario(id = 1, nombre = "Mozo Prueba", rol = "MOZO")
    )

    @Before
    fun preparar() {
        Dispatchers.setMain(testDispatcher)
        coEvery { authRepository.sesionGuardada() } returns null
    }

    @After
    fun limpiar() {
        Dispatchers.resetMain()
    }

    private fun TestScope.escucharEventos(viewModel: LoginViewModel): List<LoginEvento> {
        val eventos = mutableListOf<LoginEvento>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.eventos.toList(eventos) }
        return eventos
    }

    @Test
    fun `con campos vacios muestra error y no llama al repositorio`() = runTest(testDispatcher) {
        val viewModel = LoginViewModel(authRepository, testDispatcher)
        advanceUntilIdle()

        viewModel.ingresar()
        advanceUntilIdle()

        assertEquals(ErrorLogin.CAMPOS_VACIOS, viewModel.estado.value.error)
        coVerify(exactly = 0) { authRepository.iniciarSesion(any(), any()) }
    }

    @Test
    fun `con credenciales validas navega al mapa de mesas`() = runTest(testDispatcher) {
        coEvery { authRepository.iniciarSesion("mozo", "mozo123") } returns Resultado.Exito(sesion)

        val viewModel = LoginViewModel(authRepository, testDispatcher)
        val eventos = escucharEventos(viewModel)
        advanceUntilIdle()

        viewModel.onUsuarioCambiado("mozo")
        viewModel.onClaveCambiada("mozo123")
        viewModel.ingresar()
        advanceUntilIdle()

        assertTrue(eventos.contains(LoginEvento.IrAMesas))
        assertFalse(viewModel.estado.value.cargando)
        assertEquals(null, viewModel.estado.value.error)
    }

    @Test
    fun `con credenciales invalidas muestra el error generico`() = runTest(testDispatcher) {
        coEvery { authRepository.iniciarSesion(any(), any()) } returns
            Resultado.Fallo(ErrorApp.CREDENCIALES)

        val viewModel = LoginViewModel(authRepository, testDispatcher)
        advanceUntilIdle()

        viewModel.onUsuarioCambiado("mozo")
        viewModel.onClaveCambiada("incorrecta")
        viewModel.ingresar()
        advanceUntilIdle()

        assertEquals(ErrorLogin.CREDENCIALES, viewModel.estado.value.error)
        assertFalse(viewModel.estado.value.cargando)
    }

    @Test
    fun `un fallo de red muestra el error de conexion`() = runTest(testDispatcher) {
        coEvery { authRepository.iniciarSesion(any(), any()) } returns Resultado.Fallo(ErrorApp.RED)

        val viewModel = LoginViewModel(authRepository, testDispatcher)
        advanceUntilIdle()

        viewModel.onUsuarioCambiado("mozo")
        viewModel.onClaveCambiada("mozo123")
        viewModel.ingresar()
        advanceUntilIdle()

        assertEquals(ErrorLogin.RED, viewModel.estado.value.error)
    }

    @Test
    fun `un rol no permitido muestra su mensaje`() = runTest(testDispatcher) {
        coEvery { authRepository.iniciarSesion(any(), any()) } returns
            Resultado.Fallo(ErrorApp.ROL_NO_PERMITIDO)

        val viewModel = LoginViewModel(authRepository, testDispatcher)
        advanceUntilIdle()

        viewModel.onUsuarioCambiado("cocinero")
        viewModel.onClaveCambiada("clave")
        viewModel.ingresar()
        advanceUntilIdle()

        assertEquals(ErrorLogin.ROL_NO_PERMITIDO, viewModel.estado.value.error)
    }

    @Test
    fun `si ya hay sesion guardada navega sin pedir credenciales`() = runTest(testDispatcher) {
        coEvery { authRepository.sesionGuardada() } returns sesion

        val viewModel = LoginViewModel(authRepository, testDispatcher)
        val eventos = escucharEventos(viewModel)
        advanceUntilIdle()

        assertTrue(eventos.contains(LoginEvento.IrAMesas))
        coVerify(exactly = 0) { authRepository.iniciarSesion(any(), any()) }
    }

    @Test
    fun `el toggle de contrasena cambia el estado`() = runTest(testDispatcher) {
        val viewModel = LoginViewModel(authRepository, testDispatcher)
        advanceUntilIdle()

        assertFalse(viewModel.estado.value.mostrarClave)
        viewModel.onMostrarClaveCambiado(true)
        assertTrue(viewModel.estado.value.mostrarClave)
        viewModel.onMostrarClaveCambiado(false)
        assertFalse(viewModel.estado.value.mostrarClave)
    }

    @Test
    fun `escribir limpia el error anterior`() = runTest(testDispatcher) {
        val viewModel = LoginViewModel(authRepository, testDispatcher)
        advanceUntilIdle()

        viewModel.ingresar()
        assertEquals(ErrorLogin.CAMPOS_VACIOS, viewModel.estado.value.error)

        viewModel.onUsuarioCambiado("mozo")
        assertEquals(null, viewModel.estado.value.error)
    }
}
