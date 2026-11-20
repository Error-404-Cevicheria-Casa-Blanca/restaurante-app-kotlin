package com.lacasablanca.mesero.data.repository

import com.lacasablanca.mesero.data.local.SessionManager
import com.lacasablanca.mesero.domain.ErrorApp
import com.lacasablanca.mesero.domain.Resultado
import com.lacasablanca.mesero.domain.model.Sesion
import com.lacasablanca.mesero.domain.model.Terminal
import com.lacasablanca.mesero.domain.model.Turno
import com.lacasablanca.mesero.domain.model.Usuario
import kotlinx.coroutines.delay
import java.time.OffsetDateTime
import java.time.ZoneId

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

    /** En modo demostracion el PIN de login se valida localmente (PRB-101). */
    override suspend fun iniciarSesionPorPin(usuario: String, pin: String): Resultado<Sesion> {
        delay(latenciaMs)
        return if (usuario.isNotBlank() && pin == PIN_DEMO) {
            val sesion = Sesion(token = TOKEN_FAKE, usuario = USUARIO_FAKE)
            sessionManager.iniciar(sesion)
            Resultado.Exito(sesion)
        } else {
            Resultado.Fallo(ErrorApp.CREDENCIALES)
        }
    }

    /** En modo demostracion devuelve la sesion actual con una hora simulada. */
    override suspend fun refrescarPerfil(): Resultado<PerfilActualizado> {
        delay(latenciaMs)
        sessionManager.cargar()
        val sesion = sessionManager.sesion
            ?: return Resultado.Fallo(ErrorApp.SESION_EXPIRADA)
        return Resultado.Exito(PerfilActualizado(sesion, fechaHoraServidorFake()))
    }

    private fun fechaHoraServidorFake(): String =
        OffsetDateTime.now(zonaLima).withNano(0).toString()

    /** En modo demostracion el PIN no viaja al backend: se valida localmente. */
    override suspend fun validarPin(usuario: String, pin: String): Resultado<Unit> {
        delay(latenciaMs)
        return if (usuario.isNotBlank() && pin == PIN_DEMO) {
            Resultado.Exito(Unit)
        } else {
            Resultado.Fallo(ErrorApp.CREDENCIALES)
        }
    }

    override suspend fun cerrarSesion() {
        sessionManager.cerrar()
    }

    companion object {
        const val USUARIO_DEMO = "mozo"
        const val CLAVE_DEMO = "mozo123"
        const val PIN_DEMO = "1234"
        const val LATENCIA_SIMULADA_MS = 700L
        const val TOKEN_FAKE = "token-fake-demo"
        private val zonaLima: ZoneId = ZoneId.of("America/Lima")

        val USUARIO_FAKE = Usuario(
            id = 1L,
            nombre = "mozo",
            rol = "MOZO",
            nombreVisible = "Mozo Demo",
            turno = Turno(id = 1L, nombre = "Turno Tarde", inicio = "12:00", fin = "20:00"),
            terminal = Terminal(
                id = 1L,
                codigo = "TERM-01",
                nombre = "Tablet Salón Principal"
            )
        )
    }
}
