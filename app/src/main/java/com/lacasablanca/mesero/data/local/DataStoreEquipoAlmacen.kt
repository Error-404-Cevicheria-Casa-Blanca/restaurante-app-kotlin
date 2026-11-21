package com.lacasablanca.mesero.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.equipoDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "equipo_tablet"
)

/** Persistencia en DataStore de la URL y el terminal (PRB-99). */
class DataStoreEquipoAlmacen(context: Context) : EquipoAlmacen {

    private val dataStore = context.applicationContext.equipoDataStore

    override val cambios: Flow<EquipoGuardado> = dataStore.data.map { it.aGuardado() }

    override suspend fun leer(): EquipoGuardado = dataStore.data.first().aGuardado()

    override suspend fun guardar(datos: EquipoGuardado) {
        dataStore.edit { prefs ->
            if (datos.serverUrl.isNullOrBlank()) {
                prefs.remove(CLAVE_URL)
            } else {
                prefs[CLAVE_URL] = datos.serverUrl
            }
            if (datos.terminalCodigo.isNullOrBlank()) {
                prefs.remove(CLAVE_TERMINAL)
            } else {
                prefs[CLAVE_TERMINAL] = datos.terminalCodigo
            }
        }
    }

    private fun Preferences.aGuardado(): EquipoGuardado = EquipoGuardado(
        serverUrl = this[CLAVE_URL],
        terminalCodigo = this[CLAVE_TERMINAL]
    )

    private companion object {
        val CLAVE_URL = stringPreferencesKey("server_url")
        val CLAVE_TERMINAL = stringPreferencesKey("terminal_codigo")
    }
}
