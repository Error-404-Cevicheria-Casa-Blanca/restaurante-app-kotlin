package com.lacasablanca.mesero.ui.login

import com.lacasablanca.mesero.core.Sincronizacion
import com.lacasablanca.mesero.data.repository.AuthRepository
import com.lacasablanca.mesero.domain.ErrorApp
import com.lacasablanca.mesero.domain.Resultado
import com.lacasablanca.mesero.domain.model.Sesion
import com.lacasablanca.mesero.domain.model.Usuario
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
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

/** Flujo de login por PIN del LoginViewModel (PRB-101). */
@OptIn(ExperimentalCoroutinesApi::class)
class LoginPinViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val authRepository = mockk<AuthRepository>()
    private val sincronizacion = mockk<Sincronizacion>(relaxed = true)

    private val sesion = Sesion(
        token = "token-pin",
        usuario = Usuario(id = 1, nombre = "mozo_01", rol = "MOZO")
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
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.eventos.toList(eventos)
        }
        return eventos
    }

    private fun LoginViewModel.ingresarPin(pin: String) {
        pin.forEach { agregarDigitoPin(it) }
    }

    private fun LoginViewModel.activarPinCon(usuario: String) {
        alternarModoPin()
        onUsuarioCambiado(usuario)
    }

    @Test
    fun `el pin correcto inicia sesion y navega`() = runTest(testDispatcher) {
        coEvery { authRepository.iniciarSesionPorPin("mozo_01", "1234") } returns
            Resultado.Exito(sesion)

        val viewModel = LoginViewModel(authRepository, testDispatcher)
        val eventos = escucharEventos(viewModel)
        advanceUntilIdle()

        viewModel.activarPinCon("mozo_01")
        viewModel.ingresarPin("1234")
        advanceUntilIdle()

        assertTrue(eventos.contains(LoginEvento.IrAMesas))
        assertEquals("", viewModel.estado.value.pin)
        assertFalse(viewModel.estado.value.cargando)
        assertEquals(null, viewModel.estado.value.error)
        coVerify(exactly = 1) { authRepository.iniciarSesionPorPin("mozo_01", "1234") }
    }

    @Test
    fun `el exito del pin programa la sincronizacion`() = runTest(testDispatcher) {
        coEvery { authRepository.iniciarSesionPorPin("mozo_01", "1234") } returns
            Resultado.Exito(sesion)

        val viewModel = LoginViewModel(
            authRepository = authRepository,
            ioDispatcher = testDispatcher,
            sincronizacion = sincronizacion
        )
        val eventos = escucharEventos(viewModel)
        advanceUntilIdle()

        viewModel.activarPinCon("mozo_01")
        viewModel.ingresarPin("1234")
        advanceUntilIdle()

        assertTrue(eventos.contains(LoginEvento.IrAMesas))
        verify(exactly = 1) { sincronizacion.programar() }
    }

    @Test
    fun `un pin invalido no programa la sincronizacion`() = runTest(testDispatcher) {
        coEvery { authRepository.iniciarSesionPorPin(any(), any()) } returns
            Resultado.Fallo(ErrorApp.CREDENCIALES)

        val viewModel = LoginViewModel(
            authRepository = authRepository,
            ioDispatcher = testDispatcher,
            sincronizacion = sincronizacion
        )
        advanceUntilIdle()

        viewModel.activarPinCon("mozo_01")
        viewModel.ingresarPin("9999")
        advanceUntilIdle()

        assertEquals(ErrorLogin.PIN_INCORRECTO, viewModel.estado.value.error)
        verify(exactly = 0) { sincronizacion.programar() }
    }

    @Test
    fun `un pin invalido muestra el error y limpia el teclado`() = runTest(testDispatcher) {
        coEvery { authRepository.iniciarSesionPorPin(any(), any()) } returns
            Resultado.Fallo(ErrorApp.CREDENCIALES)

        val viewModel = LoginViewModel(authRepository, testDispatcher)
        val eventos = escucharEventos(viewModel)
        advanceUntilIdle()

        viewModel.activarPinCon("mozo_01")
        viewModel.ingresarPin("9999")
        advanceUntilIdle()

        assertEquals(ErrorLogin.PIN_INCORRECTO, viewModel.estado.value.error)
        assertEquals("", viewModel.estado.value.pin)
        assertFalse(eventos.contains(LoginEvento.IrAMesas))
    }

    @Test
    fun `una cuenta bloqueada muestra el error de bloqueo`() = runTest(testDispatcher) {
        coEvery { authRepository.iniciarSesionPorPin(any(), any()) } returns
            Resultado.Fallo(ErrorApp.BLOQUEADO)

        val viewModel = LoginViewModel(authRepository, testDispatcher)
        advanceUntilIdle()

        viewModel.activarPinCon("mozo_01")
        viewModel.ingresarPin("1234")
        advanceUntilIdle()

        assertEquals(ErrorLogin.BLOQUEADO, viewModel.estado.value.error)
        assertEquals("", viewModel.estado.value.pin)
    }

    @Test
    fun `un fallo de red muestra el error de conexion`() = runTest(testDispatcher) {
        coEvery { authRepository.iniciarSesionPorPin(any(), any()) } returns
            Resultado.Fallo(ErrorApp.RED)

        val viewModel = LoginViewModel(authRepository, testDispatcher)
        advanceUntilIdle()

        viewModel.activarPinCon("mozo_01")
        viewModel.ingresarPin("1234")
        advanceUntilIdle()

        assertEquals(ErrorLogin.RED, viewModel.estado.value.error)
    }

    @Test
    fun `sin usuario no llama a la API`() = runTest(testDispatcher) {
        val viewModel = LoginViewModel(authRepository, testDispatcher)
        advanceUntilIdle()

        viewModel.alternarModoPin()
        viewModel.ingresarPin("1234")
        advanceUntilIdle()

        assertEquals(ErrorLogin.USUARIO_REQUERIDO, viewModel.estado.value.error)
        assertEquals("", viewModel.estado.value.pin)
        coVerify(exactly = 0) { authRepository.iniciarSesionPorPin(any(), any()) }
    }

    @Test
    fun `no valida hasta completar los cuatro digitos`() = runTest(testDispatcher) {
        val viewModel = LoginViewModel(authRepository, testDispatcher)
        advanceUntilIdle()

        viewModel.activarPinCon("mozo_01")
        viewModel.ingresarPin("123")
        advanceUntilIdle()

        assertEquals("123", viewModel.estado.value.pin)
        assertFalse(viewModel.estado.value.cargando)
        coVerify(exactly = 0) { authRepository.iniciarSesionPorPin(any(), any()) }
    }

    @Test
    fun `borrar quita el ultimo digito y limpia el error`() = runTest(testDispatcher) {
        val viewModel = LoginViewModel(authRepository, testDispatcher)
        advanceUntilIdle()

        viewModel.activarPinCon("mozo_01")
        viewModel.ingresarPin("12")
        viewModel.borrarDigitoPin()

        assertEquals("1", viewModel.estado.value.pin)
        assertEquals(null, viewModel.estado.value.error)
    }
}
