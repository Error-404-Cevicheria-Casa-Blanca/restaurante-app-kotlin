package com.lacasablanca.mesero.domain.model

/**
 * Configuracion efectiva de la tablet (PRB-99): URL del servidor y codigo de
 * terminal. Los valores siempre estan resueltos; el fallback a BuildConfig se
 * aplica antes de construir esta clase.
 */
data class ConfiguracionEquipo(
    val serverUrl: String,
    val terminalCodigo: String
)
