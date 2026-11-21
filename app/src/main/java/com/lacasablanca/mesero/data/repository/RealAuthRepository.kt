package com.lacasablanca.mesero.data.repository

import com.lacasablanca.mesero.BuildConfig
import com.lacasablanca.mesero.data.local.SessionManager
import com.lacasablanca.mesero.data.remote.AuthApi
import com.lacasablanca.mesero.data.remote.dto.LoginRequestDto
import com.lacasablanca.mesero.data.remote.dto.LoginResponseDto
import com.lacasablanca.mesero.data.remote.dto.PinRequestDto
import com.lacasablanca.mesero.data.remote.dto.TerminalDto
import com.lacasablanca.mesero.data.remote.dto.TurnoDto
import com.lacasablanca.mesero.domain.ErrorApp
import com.lacasablanca.mesero.domain.Resultado
import com.lacasablanca.mesero.domain.errorDesdeCodigoHttp
import com.lacasablanca.mesero.domain.model.Sesion
import com.lacasablanca.mesero.domain.model.Terminal
import com.lacasablanca.mesero.domain.model.Turno
import com.lacasablanca.mesero.domain.model.Usuario
import retrofit2.HttpException
import java.io.IOException

/** Login contra la API real (POST /api/v1/auth/login). */
class RealAuthRepository(
    private val api: AuthApi,
    private val sessionManager: SessionManager,
    private val terminalCodigo: () -> String = { BuildConfig.TERMINAL_CODIGO }
) : AuthRepository {

    override suspend fun iniciarSesion(usuario: String, clave: String): Resultado<Sesion> {
        return try {
            val respuesta = api.login(
                LoginRequestDto(
                    usuario = usuario.trim(),
                    clave = clave,
                    terminalCodigo = terminalConfigurado()
                )
            )
            persistirSesion(respuesta)
        } catch (e: HttpException) {
            // 401 en login = credenciales invalidas; 403 = rol sin acceso (D10).
            val error = when (e.code()) {
                401 -> ErrorApp.CREDENCIALES
                403 -> ErrorApp.ROL_NO_PERMITIDO
                else -> errorDesdeCodigoHttp(e.code())
            }
            Resultado.Fallo(error)
        } catch (e: IOException) {
            Resultado.Fallo(ErrorApp.RED)
        } catch (e: Exception) {
            Resultado.Fallo(ErrorApp.DESCONOCIDO)
        }
    }

    /**
     * POST /api/v1/auth/pin (PRB-101). A diferencia de [validarPin], esta
     * operacion usa el token devuelto para crear la sesion e incluye el codigo
     * de terminal, igual que el login por credenciales.
     */
    override suspend fun iniciarSesionPorPin(usuario: String, pin: String): Resultado<Sesion> {
        return try {
            val respuesta = api.validarPin(
                PinRequestDto(
                    usuario = usuario.trim(),
                    pin = pin,
                    terminalCodigo = terminalConfigurado()
                )
            )
            persistirSesion(respuesta)
        } catch (e: HttpException) {
            val error = when (e.code()) {
                400 -> ErrorApp.VALIDACION
                401 -> ErrorApp.CREDENCIALES
                403 -> ErrorApp.ROL_NO_PERMITIDO
                423 -> ErrorApp.BLOQUEADO
                else -> errorDesdeCodigoHttp(e.code())
            }
            Resultado.Fallo(error)
        } catch (e: IOException) {
            Resultado.Fallo(ErrorApp.RED)
        } catch (e: Exception) {
            Resultado.Fallo(ErrorApp.DESCONOCIDO)
        }
    }

    override suspend fun sesionGuardada(): Sesion? {
        sessionManager.cargar()
        return sessionManager.sesion
    }

    /** Valida el rol y persiste la sesion comun al login por clave y por PIN. */
    private suspend fun persistirSesion(respuesta: LoginResponseDto): Resultado<Sesion> {
        val token = respuesta.token
        val usuarioDto = respuesta.usuario
        if (token.isNullOrBlank() || usuarioDto == null) {
            return Resultado.Fallo(ErrorApp.SERVIDOR)
        }
        val rol = usuarioDto.rol.orEmpty().uppercase()
        if (rol != ROL_MOZO && rol != ROL_ADMIN) {
            return Resultado.Fallo(ErrorApp.ROL_NO_PERMITIDO)
        }
        val sesion = Sesion(
            token = token,
            usuario = Usuario(
                id = usuarioDto.id ?: 0L,
                nombre = usuarioDto.nombre.orEmpty(),
                rol = rol,
                nombreVisible = usuarioDto.nombreVisible.orEmpty(),
                turno = usuarioDto.turno?.aDominio(),
                terminal = usuarioDto.terminal?.aDominio()
            )
        )
        sessionManager.iniciar(sesion)
        return Resultado.Exito(sesion)
    }

    /**
     * GET /api/v1/auth/me (PRB-102). Conserva el token de la sesion activa y
     * persiste los datos refrescados (nombre visible, turno y terminal).
     */
    override suspend fun refrescarPerfil(): Resultado<PerfilActualizado> {
        return try {
            val sesionActual = sesionVigente()
                ?: return Resultado.Fallo(ErrorApp.SESION_EXPIRADA)
            val respuesta = api.perfil()
            val usuarioDto = respuesta.usuario
                ?: return Resultado.Fallo(ErrorApp.SERVIDOR)
            val anterior = sesionActual.usuario
            val actualizada = Sesion(
                token = sesionActual.token,
                usuario = Usuario(
                    id = usuarioDto.id ?: anterior.id,
                    nombre = usuarioDto.nombre?.takeIf { it.isNotBlank() } ?: anterior.nombre,
                    rol = usuarioDto.rol?.uppercase()?.takeIf { it.isNotBlank() } ?: anterior.rol,
                    nombreVisible = usuarioDto.nombreVisible?.takeIf { it.isNotBlank() }
                        ?: anterior.nombreVisible,
                    turno = usuarioDto.turno?.aDominio(),
                    terminal = usuarioDto.terminal?.aDominio()
                )
            )
            sessionManager.iniciar(actualizada)
            Resultado.Exito(PerfilActualizado(actualizada, respuesta.fechaHoraServidor))
        } catch (e: HttpException) {
            Resultado.Fallo(errorDesdeCodigoHttp(e.code()))
        } catch (e: IOException) {
            Resultado.Fallo(ErrorApp.RED)
        } catch (e: Exception) {
            Resultado.Fallo(ErrorApp.DESCONOCIDO)
        }
    }

    private suspend fun sesionVigente(): Sesion? {
        sessionManager.cargar()
        return sessionManager.sesion
    }

    /**
     * POST /api/v1/auth/pin (PRB-54). El backend devuelve un token nuevo, pero
     * la app conserva el de la sesion: este endpoint solo confirma el PIN.
     */
    override suspend fun validarPin(usuario: String, pin: String): Resultado<Unit> {
        return try {
            api.validarPin(
                PinRequestDto(
                    usuario = usuario.trim(),
                    pin = pin,
                    terminalCodigo = terminalConfigurado()
                )
            )
            Resultado.Exito(Unit)
        } catch (e: HttpException) {
            val error = when (e.code()) {
                400 -> ErrorApp.VALIDACION
                401 -> ErrorApp.CREDENCIALES
                423 -> ErrorApp.BLOQUEADO
                else -> errorDesdeCodigoHttp(e.code())
            }
            Resultado.Fallo(error)
        } catch (e: IOException) {
            Resultado.Fallo(ErrorApp.RED)
        } catch (e: Exception) {
            Resultado.Fallo(ErrorApp.DESCONOCIDO)
        }
    }

    override suspend fun cerrarSesion() {
        sessionManager.cerrar()
    }

    /** Terminal configurado en Ajustes; BuildConfig solo es el valor inicial. */
    private fun terminalConfigurado(): String? =
        terminalCodigo().trim().takeIf { it.isNotBlank() }

    private fun TurnoDto.aDominio(): Turno = Turno(
        id = id ?: 0L,
        nombre = nombre.orEmpty(),
        inicio = inicio.orEmpty(),
        fin = fin.orEmpty()
    )

    private fun TerminalDto.aDominio(): Terminal = Terminal(
        id = id ?: 0L,
        codigo = codigo.orEmpty(),
        nombre = nombre.orEmpty()
    )

    private companion object {
        const val ROL_MOZO = "MOZO"
        const val ROL_ADMIN = "ADMIN"
    }
}
