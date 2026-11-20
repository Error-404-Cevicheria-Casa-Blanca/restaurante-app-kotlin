package com.lacasablanca.mesero.data.repository

import com.lacasablanca.mesero.BuildConfig
import com.lacasablanca.mesero.data.local.SessionManager
import com.lacasablanca.mesero.data.local.TokenStore
import com.lacasablanca.mesero.data.remote.AuthApi
import com.lacasablanca.mesero.data.remote.dto.LoginResponseDto
import com.lacasablanca.mesero.data.remote.dto.PinRequestDto
import com.lacasablanca.mesero.data.remote.dto.UsuarioDto
import com.lacasablanca.mesero.domain.ErrorApp
import com.lacasablanca.mesero.domain.Resultado
import com.lacasablanca.mesero.domain.model.Sesion
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/** Login por usuario y PIN contra POST /api/v1/auth/pin (PRB-101). */
class RealAuthRepositoryPinTest {

    private val api = mockk<AuthApi>()
    private val tokenStore = TokenStoreEnMemoria()
    private val sessionManager = SessionManager(tokenStore)
    private val repositorio = RealAuthRepository(api, sessionManager)

    @Test
    fun `el exito envia usuario pin y terminal y persiste la sesion`() = runTest {
        val peticion = slot<PinRequestDto>()
        coEvery { api.validarPin(capture(peticion)) } returns LoginResponseDto(
            token = "token-pin",
            tipo = "Bearer",
            usuario = UsuarioDto(id = 7L, nombre = "mozo_01", rol = "MOZO")
        )

        val resultado = repositorio.iniciarSesionPorPin(" mozo_01 ", "1234")

        assertTrue(resultado is Resultado.Exito)
        val sesion = (resultado as Resultado.Exito).datos
        assertEquals("token-pin", sesion.token)
        assertEquals("mozo_01", sesion.usuario.nombre)
        assertEquals("MOZO", sesion.usuario.rol)
        assertEquals("mozo_01", peticion.captured.usuario)
        assertEquals("1234", peticion.captured.pin)
        assertEquals(BuildConfig.TERMINAL_CODIGO, peticion.captured.terminalCodigo)
        assertEquals("token-pin", sessionManager.sesion?.token)
        assertEquals("mozo_01", tokenStore.sesion?.usuario?.nombre)
    }

    @Test
    fun `un 401 devuelve credenciales`() = runTest {
        coEvery { api.validarPin(any()) } throws errorHttp(401)

        assertEquals(
            ErrorApp.CREDENCIALES,
            errorDe(repositorio.iniciarSesionPorPin("mozo_01", "0000"))
        )
    }

    @Test
    fun `un 423 devuelve bloqueado`() = runTest {
        coEvery { api.validarPin(any()) } throws errorHttp(423)

        assertEquals(
            ErrorApp.BLOQUEADO,
            errorDe(repositorio.iniciarSesionPorPin("mozo_01", "1234"))
        )
    }

    @Test
    fun `un 400 devuelve validacion`() = runTest {
        coEvery { api.validarPin(any()) } throws errorHttp(400)

        assertEquals(
            ErrorApp.VALIDACION,
            errorDe(repositorio.iniciarSesionPorPin("mozo_01", "12"))
        )
    }

    @Test
    fun `un 403 devuelve rol no permitido`() = runTest {
        coEvery { api.validarPin(any()) } throws errorHttp(403)

        assertEquals(
            ErrorApp.ROL_NO_PERMITIDO,
            errorDe(repositorio.iniciarSesionPorPin("cocinero", "1234"))
        )
    }

    @Test
    fun `una IOException devuelve RED`() = runTest {
        coEvery { api.validarPin(any()) } throws IOException("sin red")

        assertEquals(
            ErrorApp.RED,
            errorDe(repositorio.iniciarSesionPorPin("mozo_01", "1234"))
        )
    }

    @Test
    fun `un rol distinto de MOZO o ADMIN no inicia sesion`() = runTest {
        coEvery { api.validarPin(any()) } returns LoginResponseDto(
            token = "token-pin",
            usuario = UsuarioDto(id = 9L, nombre = "cocinero", rol = "COCINERO")
        )

        assertEquals(
            ErrorApp.ROL_NO_PERMITIDO,
            errorDe(repositorio.iniciarSesionPorPin("cocinero", "1234"))
        )
        assertEquals(null, sessionManager.sesion)
    }

    private fun errorDe(resultado: Resultado<Sesion>): ErrorApp {
        assertTrue(resultado is Resultado.Fallo)
        return (resultado as Resultado.Fallo).error
    }

    private fun errorHttp(codigo: Int): HttpException {
        val cuerpo = "{\"mensaje\":\"error\"}".toResponseBody("application/json".toMediaType())
        return HttpException(Response.error<Any>(codigo, cuerpo))
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
