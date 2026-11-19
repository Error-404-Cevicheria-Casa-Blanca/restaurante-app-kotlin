package com.lacasablanca.mesero.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lacasablanca.mesero.domain.model.Sesion
import com.lacasablanca.mesero.domain.model.Terminal
import com.lacasablanca.mesero.domain.model.Turno
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
            prefs[CLAVE_USUARIO_NOMBRE_VISIBLE] = sesion.usuario.nombreVisible
            prefs[CLAVE_USUARIO_TURNO_NOMBRE] = sesion.usuario.turno?.nombre.orEmpty()
            prefs[CLAVE_USUARIO_TERMINAL_NOMBRE] = sesion.usuario.terminal?.nombre.orEmpty()
            prefs[CLAVE_USUARIO_TERMINAL_CODIGO] = sesion.usuario.terminal?.codigo.orEmpty()
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
        val turnoNombre = prefs[CLAVE_USUARIO_TURNO_NOMBRE].orEmpty()
        val terminalNombre = prefs[CLAVE_USUARIO_TERMINAL_NOMBRE].orEmpty()
        val terminalCodigo = prefs[CLAVE_USUARIO_TERMINAL_CODIGO].orEmpty()
        return Sesion(
            token = token,
            usuario = Usuario(
                id = prefs[CLAVE_USUARIO_ID] ?: 0L,
                nombre = prefs[CLAVE_USUARIO_NOMBRE].orEmpty(),
                rol = prefs[CLAVE_USUARIO_ROL].orEmpty(),
                nombreVisible = prefs[CLAVE_USUARIO_NOMBRE_VISIBLE].orEmpty(),
                turno = turnoNombre.takeIf { it.isNotBlank() }?.let { Turno(nombre = it) },
                terminal = if (terminalNombre.isNotBlank() || terminalCodigo.isNotBlank()) {
                    Terminal(codigo = terminalCodigo, nombre = terminalNombre)
                } else {
                    null
                }
            )
        )
    }

    override suspend fun limpiar() {
        dataStore.edit { prefs ->
            prefs.remove(CLAVE_TOKEN)
            prefs.remove(CLAVE_USUARIO_ID)
            prefs.remove(CLAVE_USUARIO_NOMBRE)
            prefs.remove(CLAVE_USUARIO_ROL)
            prefs.remove(CLAVE_USUARIO_NOMBRE_VISIBLE)
            prefs.remove(CLAVE_USUARIO_TURNO_NOMBRE)
            prefs.remove(CLAVE_USUARIO_TERMINAL_NOMBRE)
            prefs.remove(CLAVE_USUARIO_TERMINAL_CODIGO)
        }
    }

    private companion object {
        val CLAVE_TOKEN = stringPreferencesKey("token_cifrado")
        val CLAVE_USUARIO_ID = longPreferencesKey("usuario_id")
        val CLAVE_USUARIO_NOMBRE = stringPreferencesKey("usuario_nombre")
        val CLAVE_USUARIO_ROL = stringPreferencesKey("usuario_rol")
        val CLAVE_USUARIO_NOMBRE_VISIBLE = stringPreferencesKey("usuario_nombre_visible")
        val CLAVE_USUARIO_TURNO_NOMBRE = stringPreferencesKey("usuario_turno_nombre")
        val CLAVE_USUARIO_TERMINAL_NOMBRE = stringPreferencesKey("usuario_terminal_nombre")
        val CLAVE_USUARIO_TERMINAL_CODIGO = stringPreferencesKey("usuario_terminal_codigo")
    }
}
