package com.lacasablanca.mesero.data.local

import com.lacasablanca.mesero.domain.model.Sesion

/**
 * Sesion en memoria para que el interceptor HTTP lea el token sin bloquear.
 * La persistencia cifrada la hace [TokenStore].
 */
class SessionManager(private val tokenStore: TokenStore) {

    @Volatile
    var sesion: Sesion? = null
        private set

    val token: String? get() = sesion?.token

    suspend fun cargar() {
        if (sesion == null) sesion = tokenStore.leer()
    }

    suspend fun iniciar(nueva: Sesion) {
        sesion = nueva
        tokenStore.guardar(nueva)
    }

    suspend fun cerrar() {
        sesion = null
        tokenStore.limpiar()
    }
}
