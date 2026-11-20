package com.lacasablanca.mesero.data.repository

import com.lacasablanca.mesero.data.local.SessionManager
import com.lacasablanca.mesero.data.local.TokenStore
import com.lacasablanca.mesero.domain.ErrorApp
import com.lacasablanca.mesero.domain.Resultado
import com.lacasablanca.mesero.domain.model.Sesion
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Login por PIN con datos de demostracion (PRB-101). */
class FakeAuthRepositoryPinTest {

    private val tokenStore = TokenStoreEnMemoria()
    private val sessionManager = SessionManager(tokenStore)
    private val repositorio = FakeAuthRepository(sessionManager, latenciaMs = 0)

    @Test
    fun `el pin demo inicia sesion y la persiste`() = runTest {
        val resultado = repositorio.iniciarSesionPorPin("mozo", FakeAuthRepository.PIN_DEMO)

        assertTrue(resultado is Resultado.Exito)
        val sesion = (resultado as Resultado.Exito).datos
        assertEquals(FakeAuthRepository.TOKEN_FAKE, sesion.token)
        assertEquals(FakeAuthRepository.USUARIO_FAKE, sesion.usuario)
        assertEquals(FakeAuthRepository.TOKEN_FAKE, sessionManager.sesion?.token)
        assertEquals(FakeAuthRepository.TOKEN_FAKE, tokenStore.sesion?.token)
    }

    @Test
    fun `un pin distinto al demo falla`() = runTest {
        val resultado = repositorio.iniciarSesionPorPin("mozo", "0000")

        assertTrue(resultado is Resultado.Fallo)
        assertEquals(ErrorApp.CREDENCIALES, (resultado as Resultado.Fallo).error)
        assertEquals(null, sessionManager.sesion)
    }

    @Test
    fun `un usuario vacio no inicia sesion aunque el pin sea el demo`() = runTest {
        val resultado = repositorio.iniciarSesionPorPin("", FakeAuthRepository.PIN_DEMO)

        assertTrue(resultado is Resultado.Fallo)
        assertEquals(ErrorApp.CREDENCIALES, (resultado as Resultado.Fallo).error)
    }

    private class TokenStoreEnMemoria : TokenStore {
        var sesion: Sesion? = null
            private set

        override suspend fun guardar(sesion: Sesion) {
            this.sesion = sesion
        }

        override suspend fun leer(): Sesion? = sesion

        override suspend fun limpiar() {
            sesion = null
        }
    }
}
