package com.lacasablanca.mesero.data.repository

import com.lacasablanca.mesero.data.local.SessionManager
import com.lacasablanca.mesero.data.local.TokenStore
import com.lacasablanca.mesero.data.remote.AuthApi
import com.lacasablanca.mesero.data.remote.dto.PerfilResponseDto
import com.lacasablanca.mesero.data.remote.dto.TerminalDto
import com.lacasablanca.mesero.data.remote.dto.TurnoDto
import com.lacasablanca.mesero.data.remote.dto.UsuarioDto
import com.lacasablanca.mesero.domain.ErrorApp
import com.lacasablanca.mesero.domain.Resultado
import com.lacasablanca.mesero.domain.model.Sesion
import com.lacasablanca.mesero.domain.model.Usuario
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/** Refresco del perfil contra GET /api/v1/auth/me (PRB-102). */
class RealAuthRepositoryTest {

    private val api = mockk<AuthApi>()
    private val tokenStore = TokenStoreEnMemoria()
    private val sessionManager = SessionManager(tokenStore)
    private val repositorio = RealAuthRepository(api, sessionManager)

    @Test
    fun `el exito actualiza nombre turno y terminal y conserva el token`() = runTest {
        sessionManager.iniciar(
            Sesion(
                token = "token-vigente",
                usuario = Usuario(
                    id = 1L,
                    nombre = "mozo_01",
                    rol = "MOZO",
                    nombreVisible = "Mozo Local"
                )
            )
        )
        coEvery { api.perfil() } returns PerfilResponseDto(
            usuario = UsuarioDto(
                id = 1L,
                nombre = "mozo_01",
                nombreVisible = "David Rojas",
                rol = "MOZO",
                turno = TurnoDto(id = 2L, nombre = "Turno Noche", inicio = "18:00", fin = "23:00"),
                terminal = TerminalDto(id = 2L, codigo = "TERM-02", nombre = "Tablet Barra")
            ),
            fechaHoraServidor = "2026-11-20T18:30:00-05:00"
        )

        val resultado = repositorio.refrescarPerfil()

        assertTrue(resultado is Resultado.Exito)
        val datos = (resultado as Resultado.Exito).datos
        assertEquals("token-vigente", datos.sesion.token)
        assertEquals("David Rojas", datos.sesion.usuario.nombreVisible)
        assertEquals("Turno Noche", datos.sesion.usuario.turno?.nombre)
        assertEquals("TERM-02", datos.sesion.usuario.terminal?.codigo)
        assertEquals("2026-11-20T18:30:00-05:00", datos.fechaHoraServidor)
        assertEquals("token-vigente", sessionManager.sesion?.token)
        coVerify(exactly = 1) { api.perfil() }
    }

    @Test
    fun `un fallo de red devuelve RED`() = runTest {
        sessionManager.iniciar(
            Sesion(token = "token-vigente", usuario = Usuario(1L, "mozo_01", "MOZO"))
        )
        coEvery { api.perfil() } throws IOException("sin red")

        val resultado = repositorio.refrescarPerfil()

        assertTrue(resultado is Resultado.Fallo)
        assertEquals(ErrorApp.RED, (resultado as Resultado.Fallo).error)
    }

    @Test
    fun `un 401 devuelve sesion expirada`() = runTest {
        sessionManager.iniciar(
            Sesion(token = "token-vigente", usuario = Usuario(1L, "mozo_01", "MOZO"))
        )
        coEvery { api.perfil() } throws errorHttp(401)

        val resultado = repositorio.refrescarPerfil()

        assertTrue(resultado is Resultado.Fallo)
        assertEquals(ErrorApp.SESION_EXPIRADA, (resultado as Resultado.Fallo).error)
    }

    private fun errorHttp(codigo: Int): HttpException {
        val cuerpo = "{\"mensaje\":\"error\"}".toResponseBody("application/json".toMediaType())
        return HttpException(Response.error<Any>(codigo, cuerpo))
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
