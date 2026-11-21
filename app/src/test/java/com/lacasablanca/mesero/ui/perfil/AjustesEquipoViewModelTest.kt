package com.lacasablanca.mesero.ui.perfil

import com.lacasablanca.mesero.data.repository.ConfiguracionEquipoRepository
import com.lacasablanca.mesero.data.repository.ResultadoConfiguracion
import com.lacasablanca.mesero.domain.equipo.ErrorTerminal
import com.lacasablanca.mesero.domain.equipo.ErrorUrl
import com.lacasablanca.mesero.domain.model.ConfiguracionEquipo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
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
class AjustesEquipoViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun preparar() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun limpiar() {
        Dispatchers.resetMain()
    }

    @Test
    fun `abrir precarga la configuracion efectiva`() {
        val repo = FakeConfiguracionEquipo()
        val viewModel = AjustesEquipoViewModel(repo, testDispatcher)

        viewModel.abrir()

        val estado = viewModel.estado.value
        assertTrue(estado.abierto)
        assertEquals("http://10.0.2.2:8080/", estado.url)
        assertEquals("TERM-01", estado.terminal)
    }

    @Test
    fun `guardar una configuracion valida muestra el exito`() = runTest(testDispatcher) {
        val repo = FakeConfiguracionEquipo()
        repo.resultado = ResultadoConfiguracion.Guardado(
            ConfiguracionEquipo("https://casa-blanca.local/", "TERM-02")
        )
        val viewModel = AjustesEquipoViewModel(repo, testDispatcher)
        viewModel.abrir()
        viewModel.onUrlCambiada("https://casa-blanca.local")
        viewModel.onTerminalCambiada("TERM-02")

        viewModel.guardar()
        advanceUntilIdle()

        val estado = viewModel.estado.value
        assertTrue(estado.guardado)
        assertFalse(estado.guardando)
        assertEquals("https://casa-blanca.local/", estado.url)
        assertEquals("TERM-02", estado.terminal)
        assertEquals(listOf("https://casa-blanca.local" to "TERM-02"), repo.guardados)
    }

    @Test
    fun `guardar muestra el error de url sin guardar`() = runTest(testDispatcher) {
        val repo = FakeConfiguracionEquipo()
        repo.resultado = ResultadoConfiguracion.Invalido(errorUrl = ErrorUrl.SIN_ESQUEMA)
        val viewModel = AjustesEquipoViewModel(repo, testDispatcher)
        viewModel.abrir()
        viewModel.onUrlCambiada("casa-blanca.local")

        viewModel.guardar()
        advanceUntilIdle()

        val estado = viewModel.estado.value
        assertEquals(ErrorUrl.SIN_ESQUEMA, estado.errorUrl)
        assertFalse(estado.guardado)
        assertEquals(null, estado.errorTerminal)
    }

    @Test
    fun `guardar muestra el error de terminal vacio`() = runTest(testDispatcher) {
        val repo = FakeConfiguracionEquipo()
        repo.resultado = ResultadoConfiguracion.Invalido(errorTerminal = ErrorTerminal.VACIO)
        val viewModel = AjustesEquipoViewModel(repo, testDispatcher)
        viewModel.abrir()
        viewModel.onTerminalCambiada("")

        viewModel.guardar()
        advanceUntilIdle()

        val estado = viewModel.estado.value
        assertEquals(ErrorTerminal.VACIO, estado.errorTerminal)
        assertFalse(estado.guardado)
    }

    @Test
    fun `un fallo inesperado muestra el error general`() = runTest(testDispatcher) {
        val repo = FakeConfiguracionEquipo()
        repo.excepcion = IllegalStateException("disco lleno")
        val viewModel = AjustesEquipoViewModel(repo, testDispatcher)
        viewModel.abrir()

        viewModel.guardar()
        advanceUntilIdle()

        val estado = viewModel.estado.value
        assertTrue(estado.errorGeneral)
        assertFalse(estado.guardando)
    }

    private class FakeConfiguracionEquipo : ConfiguracionEquipoRepository {
        private val estado = MutableStateFlow(
            ConfiguracionEquipo("http://10.0.2.2:8080/", "TERM-01")
        )
        var resultado: ResultadoConfiguracion? = null
        var excepcion: Exception? = null
        val guardados = mutableListOf<Pair<String, String>>()

        override val configuracion: Flow<ConfiguracionEquipo> = estado

        override fun urlActual(): String = estado.value.serverUrl

        override fun terminalActual(): String = estado.value.terminalCodigo

        override suspend fun configuracionActual(): ConfiguracionEquipo = estado.value

        override suspend fun guardar(
            serverUrl: String,
            terminalCodigo: String
        ): ResultadoConfiguracion {
            excepcion?.let { throw it }
            guardados += serverUrl to terminalCodigo
            return resultado ?: ResultadoConfiguracion.Guardado(
                ConfiguracionEquipo(serverUrl, terminalCodigo)
            )
        }
    }
}
