package com.lacasablanca.mesero.data.local

import android.content.Context
import androidx.core.content.edit

/**
 * Copia sincrona de la configuracion para que el interceptor HTTP y el login
 * puedan leerla sin suspender. La fuente de verdad sigue siendo DataStore;
 * este espejo se actualiza en cada guardado.
 */
interface CacheEquipo {
    fun url(): String?

    fun terminal(): String?

    fun guardar(url: String, terminal: String)
}

class SharedPreferencesCacheEquipo(contexto: Context) : CacheEquipo {

    private val prefs = contexto.getSharedPreferences(NOMBRE, Context.MODE_PRIVATE)

    override fun url(): String? = prefs.getString(CLAVE_URL, null)

    override fun terminal(): String? = prefs.getString(CLAVE_TERMINAL, null)

    override fun guardar(url: String, terminal: String) {
        prefs.edit {
            putString(CLAVE_URL, url)
            putString(CLAVE_TERMINAL, terminal)
        }
    }

    private companion object {
        const val NOMBRE = "marea_equipo_cache"
        const val CLAVE_URL = "server_url"
        const val CLAVE_TERMINAL = "terminal_codigo"
    }
}
