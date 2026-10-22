package com.lacasablanca.mesero.data.local

import com.lacasablanca.mesero.domain.model.Sesion

/** Persistencia de la sesion. El token se guarda cifrado. */
interface TokenStore {
    suspend fun guardar(sesion: Sesion)
    suspend fun leer(): Sesion?
    suspend fun limpiar()
}
