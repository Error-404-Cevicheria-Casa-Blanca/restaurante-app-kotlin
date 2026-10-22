package com.lacasablanca.mesero.data.repository

import com.lacasablanca.mesero.domain.Resultado
import com.lacasablanca.mesero.domain.model.Sesion

/** Unica fuente de datos de autenticacion para la UI. */
interface AuthRepository {
    suspend fun iniciarSesion(usuario: String, clave: String): Resultado<Sesion>
    suspend fun sesionGuardada(): Sesion?
    suspend fun cerrarSesion()
}
