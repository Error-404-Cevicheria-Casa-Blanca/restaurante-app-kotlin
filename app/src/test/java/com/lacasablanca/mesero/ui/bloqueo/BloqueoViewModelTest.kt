package com.lacasablanca.mesero.ui.bloqueo

import com.lacasablanca.mesero.data.local.SessionManager
import com.lacasablanca.mesero.data.local.TokenStore
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
import kotlinx.coroutines.delay
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
class BloqueoViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val authRepository = mockk<AuthRepository>()

    private val sesion = Sesion(
        token = "token-prueba",
        usuario = Usuario(id = 1, nombre = "Mozo Prueba", rol = "MOZO")
    )

    private lateinit var tokenStore: TokenStoreFake
    private lateinit var sessionManager: SessionManager

    @Before
    fun preparar() {
        Dispatchers.setMain(testDispatcher)
        tokenStore = TokenStoreFake(sesion)
        sessionManager = SessionManager(tokenStore)
    }

    @After
    fun limpiar() {
        Dispatchers.resetMain()
    }

    private fun crearViewModel(): BloqueoViewModel =
        BloqueoViewModel(authRepository, sessionManager, testDispatcher)

    private fun TestScope.escucharEventos(viewModel: BloqueoViewModel): List<BloqueoEvento> {
        val eventos = mutableListOf<BloqueoEvento>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.eventos.toList(eventos)
        }
        return eventos
    }

    private fun BloqueoViewModel.ingresarPin(pin: String) {
        pin.forEach { agregarDigito(it) }
    }

    @Test
    fun `resuelve el usuario de la sesion activa`() = runTest(testDispatcher) {
        val viewModel = crearViewModel()
        advanceUntilIdle()

        assertEquals("Mozo Prueba", viewModel.estado.value.usuario)
    }

    @Test
    fun `no valida hasta completar los cuatro digitos`() = runTest(testDispatcher) {
        val viewModel = crearViewModel()
        advanceUntilIdle()

        viewModel.ingresarPin("123")
        advanceUntilIdle()

        assertEquals("123", viewModel.estado.value.pin)
        assertFalse(viewModel.estado.value.cargando)
        coVerify(exactly = 0) { authRepository.validarPin(any(), any()) }
    }

    @Test
    fun `con el pin correcto emite desbloqueado y limpia el teclado`() = runTest(testDispatcher) {
        coEvery { authRepository.validarPin("Mozo Prueba", "1234") } returns Resultado.Exito(Unit)

        val viewModel = crearViewModel()
        val eventos = escucharEventos(viewModel)
        advanceUntilIdle()

        viewModel.ingresarPin("1234")
        advanceUntilIdle()

        assertTrue(eventos.contains(BloqueoEvento.Desbloqueado))
        assertFalse(viewModel.estado.value.cargando)
        assertEquals("", viewModel.estado.value.pin)
        assertEquals(null, viewModel.estado.value.error)
        coVerify(exactly = 1) { authRepository.validarPin("Mozo Prueba", "1234") }
    }

    @Test
    fun `con el pin incorrecto muestra error y limpia el teclado`() = runTest(testDispatcher) {
        coEvery { authRepository.validarPin(any(), any()) } returns
            Resultado.Fallo(ErrorApp.CREDENCIALES)

        val viewModel = crearViewModel()
        val eventos = escucharEventos(viewModel)
        advanceUntilIdle()

        viewModel.ingresarPin("9999")
        advanceUntilIdle()

        assertEquals(ErrorBloqueo.PIN_INCORRECTO, viewModel.estado.value.error)
        assertEquals("", viewModel.estado.value.pin)
        assertFalse(eventos.contains(BloqueoEvento.Desbloqueado))
    }

    @Test
    fun `tras un pin incorrecto se puede reintentar`() = runTest(testDispatcher) {
        coEvery { authRepository.validarPin("Mozo Prueba", "9999") } returns
            Resultado.Fallo(ErrorApp.CREDENCIALES)
        coEvery { authRepository.validarPin("Mozo Prueba", "1234") } returns
            Resultado.Exito(Unit)

        val viewModel = crearViewModel()
        val eventos = escucharEventos(viewModel)
        advanceUntilIdle()

        viewModel.ingresarPin("9999")
        advanceUntilIdle()
        assertEquals(ErrorBloqueo.PIN_INCORRECTO, viewModel.estado.value.error)

        viewModel.ingresarPin("1234")
        advanceUntilIdle()

        assertEquals(null, viewModel.estado.value.error)
        assertTrue(eventos.contains(BloqueoEvento.Desbloqueado))
    }

    @Test
    fun `borrar quita el ultimo digito y limpia el error`() = runTest(testDispatcher) {
        coEvery { authRepository.validarPin(any(), any()) } returns
            Resultado.Fallo(ErrorApp.CREDENCIALES)

        val viewModel = crearViewModel()
        advanceUntilIdle()

        viewModel.ingresarPin("12")
        assertEquals("12", viewModel.estado.value.pin)

        viewModel.borrarDigito()
        assertEquals("1", viewModel.estado.value.pin)

        viewModel.borrarDigito()
        assertEquals("", viewModel.estado.value.pin)

        viewModel.ingresarPin("9999")
        advanceUntilIdle()
        assertEquals(ErrorBloqueo.PIN_INCORRECTO, viewModel.estado.value.error)

        viewModel.borrarDigito()
        assertEquals(null, viewModel.estado.value.error)
    }

    @Test
    fun `ignora caracteres que no son digitos`() = runTest(testDispatcher) {
        val viewModel = crearViewModel()
        advanceUntilIdle()

        viewModel.agregarDigito('a')
        viewModel.agregarDigito('-')
        viewModel.agregarDigito(' ')

        assertEquals("", viewModel.estado.value.pin)
        coVerify(exactly = 0) { authRepository.validarPin(any(), any()) }
    }

    @Test
    fun `no acepta un quinto digito ni repite la validacion`() = runTest(testDispatcher) {
        coEvery { authRepository.validarPin(any(), any()) } returns Resultado.Exito(Unit)

        val viewModel = crearViewModel()
        advanceUntilIdle()

        viewModel.ingresarPin("12345")
        advanceUntilIdle()

        assertEquals("", viewModel.estado.value.pin)
        coVerify(exactly = 1) { authRepository.validarPin("Mozo Prueba", "1234") }
    }

    @Test
    fun `mientras verifica ignora nuevas pulsaciones`() = runTest(testDispatcher) {
        coEvery { authRepository.validarPin(any(), any()) } coAnswers {
            delay(1_000)
            Resultado.Exito(Unit)
        }

        val viewModel = crearViewModel()
        advanceUntilIdle()

        viewModel.ingresarPin("1234")
        assertTrue(viewModel.estado.value.cargando)

        viewModel.agregarDigito('9')
        assertEquals("1234", viewModel.estado.value.pin)

        advanceUntilIdle()
        assertEquals("", viewModel.estado.value.pin)
        assertFalse(viewModel.estado.value.cargando)
    }

    @Test
    fun `sin sesion no llama al repositorio y avisa sesion expirada`() = runTest(testDispatcher) {
        tokenStore = TokenStoreFake(null)
        sessionManager = SessionManager(tokenStore)

        val viewModel = crearViewModel()
        val eventos = escucharEventos(viewModel)
        advanceUntilIdle()

        viewModel.ingresarPin("1234")
        advanceUntilIdle()

        assertEquals(ErrorBloqueo.SESION, viewModel.estado.value.error)
        assertTrue(eventos.contains(BloqueoEvento.SesionExpirada))
        coVerify(exactly = 0) { authRepository.validarPin(any(), any()) }
    }

    @Test
    fun `un fallo de red se muestra como error de conexion`() = runTest(testDispatcher) {
        coEvery { authRepository.validarPin(any(), any()) } returns Resultado.Fallo(ErrorApp.RED)

        val viewModel = crearViewModel()
        advanceUntilIdle()

        viewModel.ingresarPin("1234")
        advanceUntilIdle()

        assertEquals(ErrorBloqueo.RED, viewModel.estado.value.error)
    }

    @Test
    fun `una cuenta bloqueada muestra el mensaje de bloqueo`() = runTest(testDispatcher) {
        coEvery { authRepository.validarPin(any(), any()) } returns
            Resultado.Fallo(ErrorApp.BLOQUEADO)

        val viewModel = crearViewModel()
        advanceUntilIdle()

        viewModel.ingresarPin("1234")
        advanceUntilIdle()

        assertEquals(ErrorBloqueo.BLOQUEADO, viewModel.estado.value.error)
    }

    @Test
    fun `una sesion expirada en el backend vuelve al login`() = runTest(testDispatcher) {
        coEvery { authRepository.validarPin(any(), any()) } returns
            Resultado.Fallo(ErrorApp.SESION_EXPIRADA)

        val viewModel = crearViewModel()
        val eventos = escucharEventos(viewModel)
        advanceUntilIdle()

        viewModel.ingresarPin("1234")
        advanceUntilIdle()

        assertEquals(ErrorBloqueo.SESION, viewModel.estado.value.error)
        assertTrue(eventos.contains(BloqueoEvento.SesionExpirada))
    }

    @Test
    fun `un fallo inesperado del repositorio muestra error generico`() = runTest(testDispatcher) {
        coEvery { authRepository.validarPin(any(), any()) } throws
            IllegalStateException("fallo simulado")

        val viewModel = crearViewModel()
        advanceUntilIdle()

        viewModel.ingresarPin("1234")
        advanceUntilIdle()

        assertEquals(ErrorBloqueo.DESCONOCIDO, viewModel.estado.value.error)
        assertEquals("", viewModel.estado.value.pin)
    }

    @Test
    fun `si falla la lectura de la sesion avisa sesion expirada`() = runTest(testDispatcher) {
        sessionManager = SessionManager(TokenStoreQueFalla())

        val viewModel = crearViewModel()
        val eventos = escucharEventos(viewModel)
        advanceUntilIdle()

        viewModel.ingresarPin("1234")
        advanceUntilIdle()

        assertEquals(ErrorBloqueo.SESION, viewModel.estado.value.error)
        assertTrue(eventos.contains(BloqueoEvento.SesionExpirada))
        coVerify(exactly = 0) { authRepository.validarPin(any(), any()) }
    }

    /** Almacen en memoria para no depender de DataStore ni del Keystore en JVM. */
    private class TokenStoreFake(private var sesion: Sesion?) : TokenStore {

        override suspend fun guardar(sesion: Sesion) {
            this.sesion = sesion
        }

        override suspend fun leer(): Sesion? = sesion

        override suspend fun limpiar() {
            sesion = null
        }
    }

    /** Simula un Keystore corrupto para verificar que la app no se cierra. */
    private class TokenStoreQueFalla : TokenStore {

        override suspend fun guardar(sesion: Sesion) = Unit

        override suspend fun leer(): Sesion? = error("keystore simulado")

        override suspend fun limpiar() = Unit
    }
}
