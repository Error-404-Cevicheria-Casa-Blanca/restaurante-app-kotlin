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
        try {
            tokenStore.guardar(nueva)
        } catch (e: Exception) {
            // Si falla la persistencia (p. ej. Keystore), la sesion sigue valida en memoria.
        }
    }

    suspend fun cerrar() {
        sesion = null
        try {
            tokenStore.limpiar()
        } catch (e: Exception) {
            // El logout debe completarse aunque falle el borrado fisico.
        }
    }
}
