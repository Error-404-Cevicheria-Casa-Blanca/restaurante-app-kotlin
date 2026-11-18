package com.lacasablanca.mesero.domain.model

import androidx.appcompat.app.AppCompatDelegate

/**
 * Temas visuales disponibles en Perfil > Apariencia (Marea Diurna / Marea Nocturna).
 */
enum class TemaApp(val clave: String, val modoNoche: Int) {
    CLARO("claro", AppCompatDelegate.MODE_NIGHT_NO),
    OSCURO("oscuro", AppCompatDelegate.MODE_NIGHT_YES),
    AUTOMATICO("automatico", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);

    companion object {
        val POR_DEFECTO: TemaApp = OSCURO

        fun desdeClave(clave: String?): TemaApp =
            entries.firstOrNull { it.clave == clave } ?: POR_DEFECTO
    }
}
