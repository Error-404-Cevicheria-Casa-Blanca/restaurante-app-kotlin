package com.lacasablanca.mesero.data.repository

import com.lacasablanca.mesero.data.local.SessionManager
import com.lacasablanca.mesero.data.local.TokenStore
import com.lacasablanca.mesero.data.remote.AuthApi
import com.lacasablanca.mesero.data.remote.dto.LoginRequestDto
import com.lacasablanca.mesero.data.remote.dto.LoginResponseDto
import com.lacasablanca.mesero.data.remote.dto.PinRequestDto
import com.lacasablanca.mesero.data.remote.dto.UsuarioDto
import com.lacasablanca.mesero.domain.Resultado
import com.lacasablanca.mesero.domain.model.Sesion
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** El terminal viaja desde Ajustes en login, login por PIN y desbloqueo (PRB-99). */
class RealAuthRepositoryTerminalTest {

    private val api = mockk<AuthApi>()
    private val sessionManager = SessionManager(TokenStoreEnMemoria())
    private val repositorio = RealAuthRepository(api, sessionManager) { "TERM-02" }

    @Test
    fun `el login envia el terminal configurado`() = runTest {
        val peticion = slot<LoginRequestDto>()
        coEvery { api.login(capture(peticion)) } returns respuestaLogin()

        val resultado = repositorio.iniciarSesion("mozo_01", "clave")

        assertTrue(resultado is Resultado.Exito)
        assertEquals("TERM-02", peticion.captured.terminalCodigo)
        assertEquals("mozo_01", peticion.captured.usuario)
    }

    @Test
    fun `el login por pin envia el terminal configurado`() = runTest {
        val peticion = slot<PinRequestDto>()
        coEvery { api.validarPin(capture(peticion)) } returns respuestaLogin()

        val resultado = repositorio.iniciarSesionPorPin("mozo_01", "1234")

        assertTrue(resultado is Resultado.Exito)
        assertEquals("TERM-02", peticion.captured.terminalCodigo)
        assertEquals("1234", peticion.captured.pin)
    }

    @Test
    fun `el desbloqueo por pin envia el terminal configurado`() = runTest {
        val peticion = slot<PinRequestDto>()
        coEvery { api.validarPin(capture(peticion)) } returns respuestaLogin()

        val resultado = repositorio.validarPin("mozo_01", "1234")

        assertTrue(resultado is Resultado.Exito)
        assertEquals("TERM-02", peticion.captured.terminalCodigo)
    }

    private fun respuestaLogin(): LoginResponseDto = LoginResponseDto(
        token = "token-prueba",
        tipo = "Bearer",
        usuario = UsuarioDto(id = 1L, nombre = "mozo_01", rol = "MOZO")
    )

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
