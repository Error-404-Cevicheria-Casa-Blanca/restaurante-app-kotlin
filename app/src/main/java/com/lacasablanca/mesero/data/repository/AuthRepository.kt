package com.lacasablanca.mesero.data.repository

import com.lacasablanca.mesero.domain.Resultado
import com.lacasablanca.mesero.domain.model.Sesion

/** Unica fuente de datos de autenticacion para la UI. */
interface AuthRepository {
    suspend fun iniciarSesion(usuario: String, clave: String): Resultado<Sesion>
    suspend fun sesionGuardada(): Sesion?

    /**
     * Confirma la identidad del usuario de la sesion con su PIN de 4 digitos.
     * No reemplaza el token actual: solo desbloquea la pantalla (PRB-81).
     */
    suspend fun validarPin(usuario: String, pin: String): Resultado<Unit>

    suspend fun cerrarSesion()
}
