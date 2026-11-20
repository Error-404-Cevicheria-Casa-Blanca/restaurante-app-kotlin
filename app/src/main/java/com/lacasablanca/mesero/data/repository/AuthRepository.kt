package com.lacasablanca.mesero.data.repository

import com.lacasablanca.mesero.domain.Resultado
import com.lacasablanca.mesero.domain.model.Sesion

/** Resultado de refrescar el perfil: la sesion persistida y la hora del servidor. */
data class PerfilActualizado(
    val sesion: Sesion,
    val fechaHoraServidor: String?
)

/** Unica fuente de datos de autenticacion para la UI. */
interface AuthRepository {
    suspend fun iniciarSesion(usuario: String, clave: String): Resultado<Sesion>

    /**
     * Inicia sesion con usuario y PIN de 4 digitos (PRB-101). A diferencia de
     * [validarPin], esta operacion crea y persiste una sesion con el token que
     * devuelve el backend.
     */
    suspend fun iniciarSesionPorPin(usuario: String, pin: String): Resultado<Sesion>

    suspend fun sesionGuardada(): Sesion?

    /**
     * Refresca nombre visible, turno y terminal desde GET /api/v1/auth/me sin
     * re-login. Conserva el token actual y persiste la sesion actualizada.
     */
    suspend fun refrescarPerfil(): Resultado<PerfilActualizado>

    /**
     * Confirma la identidad del usuario de la sesion con su PIN de 4 digitos.
     * No reemplaza el token actual: solo desbloquea la pantalla (PRB-81).
     */
    suspend fun validarPin(usuario: String, pin: String): Resultado<Unit>

    suspend fun cerrarSesion()
}
