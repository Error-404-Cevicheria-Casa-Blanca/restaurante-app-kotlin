package com.lacasablanca.mesero.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lacasablanca.mesero.domain.model.Sesion
import com.lacasablanca.mesero.domain.model.Usuario
import kotlinx.coroutines.flow.first

private val Context.dataStoreSesion: DataStore<Preferences> by preferencesDataStore(name = "sesion_mesero")

/**
 * Sesion en DataStore. El JWT se guarda CIFRADO (AES/GCM con clave del Keystore);
 * los datos del usuario (id, nombre, rol) no son secretos y van en claro.
 */
class EncryptedTokenStore(
    context: Context,
    private val crypto: CryptoManager
) : TokenStore {

    private val dataStore = context.applicationContext.dataStoreSesion

    override suspend fun guardar(sesion: Sesion) {
        val tokenCifrado = crypto.cifrar(sesion.token)
        dataStore.edit { prefs ->
            prefs[CLAVE_TOKEN] = tokenCifrado
            prefs[CLAVE_USUARIO_ID] = sesion.usuario.id
            prefs[CLAVE_USUARIO_NOMBRE] = sesion.usuario.nombre
            prefs[CLAVE_USUARIO_ROL] = sesion.usuario.rol
        }
    }

    override suspend fun leer(): Sesion? {
        val prefs = dataStore.data.first()
        val tokenCifrado = prefs[CLAVE_TOKEN] ?: return null
        val token = crypto.descifrar(tokenCifrado)
        if (token.isNullOrBlank()) {
            limpiar()
            return null
        }
        return Sesion(
            token = token,
            usuario = Usuario(
                id = prefs[CLAVE_USUARIO_ID] ?: 0L,
                nombre = prefs[CLAVE_USUARIO_NOMBRE].orEmpty(),
                rol = prefs[CLAVE_USUARIO_ROL].orEmpty()
            )
        )
    }

    override suspend fun limpiar() {
        dataStore.edit { prefs ->
            prefs.remove(CLAVE_TOKEN)
            prefs.remove(CLAVE_USUARIO_ID)
            prefs.remove(CLAVE_USUARIO_NOMBRE)
            prefs.remove(CLAVE_USUARIO_ROL)
        }
    }

    private companion object {
        val CLAVE_TOKEN = stringPreferencesKey("token_cifrado")
        val CLAVE_USUARIO_ID = longPreferencesKey("usuario_id")
        val CLAVE_USUARIO_NOMBRE = stringPreferencesKey("usuario_nombre")
        val CLAVE_USUARIO_ROL = stringPreferencesKey("usuario_rol")
    }
}
