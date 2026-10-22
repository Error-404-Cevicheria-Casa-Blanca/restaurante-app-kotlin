package com.lacasablanca.mesero.data.repository

import com.lacasablanca.mesero.data.local.SessionManager
import com.lacasablanca.mesero.data.remote.AuthApi
import com.lacasablanca.mesero.data.remote.dto.LoginRequestDto
import com.lacasablanca.mesero.domain.ErrorApp
import com.lacasablanca.mesero.domain.Resultado
import com.lacasablanca.mesero.domain.errorDesdeCodigoHttp
import com.lacasablanca.mesero.domain.model.Sesion
import com.lacasablanca.mesero.domain.model.Usuario
import retrofit2.HttpException
import java.io.IOException

/** Login contra la API real (POST /api/v1/auth/login). */
class RealAuthRepository(
    private val api: AuthApi,
    private val sessionManager: SessionManager
) : AuthRepository {

    override suspend fun iniciarSesion(usuario: String, clave: String): Resultado<Sesion> {
        return try {
            val respuesta = api.login(LoginRequestDto(usuario = usuario.trim(), clave = clave))
            val token = respuesta.token
            val usuarioDto = respuesta.usuario
            if (token.isNullOrBlank() || usuarioDto == null) {
                Resultado.Fallo(ErrorApp.SERVIDOR)
            } else {
                val rol = usuarioDto.rol.orEmpty().uppercase()
                if (rol != ROL_MOZO && rol != ROL_ADMIN) {
                    Resultado.Fallo(ErrorApp.ROL_NO_PERMITIDO)
                } else {
                    val sesion = Sesion(
                        token = token,
                        usuario = Usuario(
                            id = usuarioDto.id ?: 0L,
                            nombre = usuarioDto.nombre.orEmpty(),
                            rol = rol
                        )
                    )
                    sessionManager.iniciar(sesion)
                    Resultado.Exito(sesion)
                }
            }
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

    override suspend fun sesionGuardada(): Sesion? {
        sessionManager.cargar()
        return sessionManager.sesion
    }

    override suspend fun cerrarSesion() {
        sessionManager.cerrar()
    }

    private companion object {
        const val ROL_MOZO = "MOZO"
        const val ROL_ADMIN = "ADMIN"
    }
}
