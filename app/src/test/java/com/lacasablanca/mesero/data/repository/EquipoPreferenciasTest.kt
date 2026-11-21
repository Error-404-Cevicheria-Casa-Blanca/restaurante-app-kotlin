package com.lacasablanca.mesero.data.repository

import com.lacasablanca.mesero.data.local.CacheEquipo
import com.lacasablanca.mesero.data.local.EquipoAlmacen
import com.lacasablanca.mesero.data.local.EquipoGuardado
import com.lacasablanca.mesero.data.local.SessionManager
import com.lacasablanca.mesero.data.local.TokenStore
import com.lacasablanca.mesero.domain.equipo.ErrorTerminal
import com.lacasablanca.mesero.domain.equipo.ErrorUrl
import com.lacasablanca.mesero.domain.model.Sesion
import com.lacasablanca.mesero.domain.model.Usuario
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EquipoPreferenciasTest {

    private val almacen = FakeEquipoAlmacen()
    private val cache = FakeCacheEquipo()
    private val urlPorDefecto = "http://10.0.2.2:8080/"
    private val terminalPorDefecto = "TERM-01"

    private fun repositorio(permitirHttp: Boolean = false) = EquipoPreferencias(
        almacen = almacen,
        cache = cache,
        urlPorDefecto = urlPorDefecto,
        terminalPorDefecto = terminalPorDefecto,
        permitirHttp = permitirHttp
    )

    @Test
    fun `una url https valida se guarda normalizada y se vuelve a leer`() = runTest {
        val repo = repositorio()

        val resultado = repo.guardar("https://casa-blanca.local", "TERM-02")

        val guardado = resultado as ResultadoConfiguracion.Guardado
        assertEquals("https://casa-blanca.local/", guardado.configuracion.serverUrl)
        assertEquals("TERM-02", guardado.configuracion.terminalCodigo)
        assertEquals(EquipoGuardado("https://casa-blanca.local/", "TERM-02"), almacen.datos)
        assertEquals("https://casa-blanca.local/", repo.urlActual())
        assertEquals("TERM-02", repo.terminalActual())
        assertEquals("https://casa-blanca.local/", repo.configuracionActual().serverUrl)
    }

    @Test
    fun `una url sin esquema devuelve error y no guarda`() = runTest {
        val resultado = repositorio().guardar("casa-blanca.local", "TERM-02")

        val invalido = resultado as ResultadoConfiguracion.Invalido
        assertEquals(ErrorUrl.SIN_ESQUEMA, invalido.errorUrl)
        assertEquals(0, almacen.guardados)
        assertEquals(null, cache.url())
    }

    @Test
    fun `una url con credenciales devuelve error y no guarda`() = runTest {
        val resultado = repositorio().guardar("https://mozo:clave@casa-blanca.local/", "TERM-02")

        val invalido = resultado as ResultadoConfiguracion.Invalido
        assertEquals(ErrorUrl.CREDENCIALES, invalido.errorUrl)
        assertEquals(0, almacen.guardados)
    }

    @Test
    fun `http se rechaza cuando no es debug`() = runTest {
        val resultado = repositorio(permitirHttp = false)
            .guardar("http://10.0.2.2:8080/", "TERM-02")

        val invalido = resultado as ResultadoConfiguracion.Invalido
        assertEquals(ErrorUrl.ESQUEMA_NO_PERMITIDO, invalido.errorUrl)
        assertEquals(0, almacen.guardados)
    }

    @Test
    fun `http se acepta en debug`() = runTest {
        val resultado = repositorio(permitirHttp = true)
            .guardar("http://10.0.2.2:8080", "TERM-02")

        val guardado = resultado as ResultadoConfiguracion.Guardado
        assertEquals("http://10.0.2.2:8080/", guardado.configuracion.serverUrl)
    }

    @Test
    fun `una url con parametros de consulta devuelve error`() = runTest {
        val resultado = repositorio().guardar("https://casa-blanca.local/?debug=1", "TERM-02")

        val invalido = resultado as ResultadoConfiguracion.Invalido
        assertEquals(ErrorUrl.CONSULTA, invalido.errorUrl)
        assertEquals(0, almacen.guardados)
    }

    @Test
    fun `un terminal vacio devuelve error claro y no guarda`() = runTest {
        val resultado = repositorio().guardar("https://casa-blanca.local/", "  ")

        val invalido = resultado as ResultadoConfiguracion.Invalido
        assertEquals(ErrorTerminal.VACIO, invalido.errorTerminal)
        assertEquals(0, almacen.guardados)
    }

    @Test
    fun `un terminal con formato invalido devuelve error y no guarda`() = runTest {
        val resultado = repositorio().guardar("https://casa-blanca.local/", "TERM 01")

        val invalido = resultado as ResultadoConfiguracion.Invalido
        assertEquals(ErrorTerminal.FORMATO, invalido.errorTerminal)
        assertEquals(0, almacen.guardados)
    }

    @Test
    fun `sin valores guardados usa BuildConfig como fallback`() = runTest {
        val repo = repositorio()

        assertEquals(urlPorDefecto, repo.urlActual())
        assertEquals(terminalPorDefecto, repo.terminalActual())
        assertEquals(urlPorDefecto, repo.configuracionActual().serverUrl)
        assertEquals(terminalPorDefecto, repo.configuracionActual().terminalCodigo)
        assertEquals(urlPorDefecto, repo.configuracion.first().serverUrl)
        assertEquals(terminalPorDefecto, repo.configuracion.first().terminalCodigo)
    }

    @Test
    fun `recrear el repositorio conserva lo guardado`() = runTest {
        repositorio().guardar("https://casa-blanca.local/", "TERM-02")

        val recreado = repositorio()

        assertEquals("https://casa-blanca.local/", recreado.urlActual())
        assertEquals("TERM-02", recreado.terminalActual())
        assertEquals("TERM-02", recreado.configuracionActual().terminalCodigo)
    }

    @Test
    fun `guardar la configuracion no altera la sesion activa`() = runTest {
        val sessionManager = SessionManager(TokenStoreEnMemoria())
        sessionManager.iniciar(Sesion(token = "token-vigente", usuario = Usuario(1L, "mozo", "MOZO")))

        repositorio().guardar("https://casa-blanca.local/", "TERM-02")
        sessionManager.cargar()

        val sesion = sessionManager.sesion
        assertTrue(sesion != null)
        assertEquals("token-vigente", sesion?.token)
        assertEquals("mozo", sesion?.usuario?.nombre)
    }

    private class FakeEquipoAlmacen : EquipoAlmacen {
        private val flujo = MutableStateFlow(EquipoGuardado())
        var guardados = 0
            private set

        val datos: EquipoGuardado get() = flujo.value

        override val cambios: Flow<EquipoGuardado> = flujo

        override suspend fun leer(): EquipoGuardado = flujo.value

        override suspend fun guardar(datos: EquipoGuardado) {
            guardados++
            flujo.value = datos
        }
    }

    private class FakeCacheEquipo : CacheEquipo {
        private var urlGuardada: String? = null
        private var terminalGuardado: String? = null

        override fun url(): String? = urlGuardada

        override fun terminal(): String? = terminalGuardado

        override fun guardar(url: String, terminal: String) {
            urlGuardada = url
            terminalGuardado = terminal
        }
    }

    private class TokenStoreEnMemoria : TokenStore {
        private var sesion: Sesion? = null

        override suspend fun guardar(sesion: Sesion) {
            this.sesion = sesion
        }

        override suspend fun leer(): Sesion? = sesion

        override suspend fun limpiar() {
            sesion = null
        }
    }
}
