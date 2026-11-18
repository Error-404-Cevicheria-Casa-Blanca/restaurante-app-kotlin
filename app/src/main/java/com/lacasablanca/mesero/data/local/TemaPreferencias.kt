package com.lacasablanca.mesero.data.local

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lacasablanca.mesero.domain.model.TemaApp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.aparienciaDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "apariencia_marea"
)

/**
 * Guarda la preferencia de apariencia en DataStore y mantiene una copia
 * sincrona ligera para aplicar el tema al instante al abrir la app.
 */
class TemaPreferencias(private val contexto: Context) {

    private val cacheSincrona = contexto.getSharedPreferences(NOMBRE_CACHE, Context.MODE_PRIVATE)

    val temaActual: Flow<TemaApp> = contexto.aparienciaDataStore.data.map { prefs ->
        TemaApp.desdeClave(prefs[CLAVE_TEMA] ?: cacheSincrona.getString(KEY_TEMA, null))
    }

    fun leerTemaSincrono(): TemaApp =
        TemaApp.desdeClave(cacheSincrona.getString(KEY_TEMA, null))

    fun aplicarTemaGuardado() {
        aplicarModo(leerTemaSincrono())
    }

    suspend fun guardarYAplicar(tema: TemaApp) {
        cacheSincrona.edit().putString(KEY_TEMA, tema.clave).apply()
        contexto.aparienciaDataStore.edit { prefs ->
            prefs[CLAVE_TEMA] = tema.clave
        }
        aplicarModo(tema)
    }

    private fun aplicarModo(tema: TemaApp) {
        if (AppCompatDelegate.getDefaultNightMode() != tema.modoNoche) {
            AppCompatDelegate.setDefaultNightMode(tema.modoNoche)
        }
    }

    private companion object {
        const val NOMBRE_CACHE = "marea_tema_cache"
        const val KEY_TEMA = "tema_app"
        val CLAVE_TEMA = stringPreferencesKey("tema_app")
    }
}
