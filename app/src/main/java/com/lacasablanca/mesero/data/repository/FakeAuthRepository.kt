package com.lacasablanca.mesero.data.repository

import com.lacasablanca.mesero.data.local.SessionManager
import com.lacasablanca.mesero.domain.ErrorApp
import com.lacasablanca.mesero.domain.Resultado
import com.lacasablanca.mesero.domain.model.Sesion
import com.lacasablanca.mesero.domain.model.Usuario
import kotlinx.coroutines.delay

/**
 * DATOS FAKE (no reales). Se usa cuando BuildConfig.USE_FAKE_DATA = true,
 * es decir, mientras el backend no esta levantado en la red local.
 *
 * Credenciales de demostracion: usuario "mozo" / clave "mozo123".
 */
class FakeAuthRepository(
    private val sessionManager: SessionManager,
    private val latenciaMs: Long = LATENCIA_SIMULADA_MS
) : AuthRepository {

    override suspend fun iniciarSesion(usuario: String, clave: String): Resultado<Sesion> {
        delay(latenciaMs)
        val usuarioValido = usuario.trim().equals(USUARIO_DEMO, ignoreCase = true)
        return if (usuarioValido && clave == CLAVE_DEMO) {
            val sesion = Sesion(token = TOKEN_FAKE, usuario = USUARIO_FAKE)
            sessionManager.iniciar(sesion)
            Resultado.Exito(sesion)
        } else {
            Resultado.Fallo(ErrorApp.CREDENCIALES)
        }
    }

    override suspend fun sesionGuardada(): Sesion? {
        sessionManager.cargar()
        return sessionManager.sesion
    }

    override suspend fun cerrarSesion() {
        sessionManager.cerrar()
    }

    companion object {
        const val USUARIO_DEMO = "mozo"
        const val CLAVE_DEMO = "mozo123"
        const val LATENCIA_SIMULADA_MS = 700L
        const val TOKEN_FAKE = "token-fake-demo"

        val USUARIO_FAKE = Usuario(id = 1L, nombre = "Mozo Demo", rol = "MOZO")
    }
}
