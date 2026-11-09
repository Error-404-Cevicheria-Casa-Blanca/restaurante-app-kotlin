package com.lacasablanca.mesero.ui.bloqueo

/** Errores de la verificacion del PIN que la pantalla traduce a strings. */
enum class ErrorBloqueo {
    PIN_INCORRECTO,
    RED,
    BLOQUEADO,
    SESION,
    DESCONOCIDO
}

data class BloqueoUiState(
    val pin: String = "",
    val usuario: String = "",
    val cargando: Boolean = false,
    val error: ErrorBloqueo? = null
) {
    val digitosIngresados: Int get() = pin.length

    companion object {
        const val LONGITUD_PIN = 4
    }
}

/** Eventos de un solo uso: desbloquear o volver al login si la sesion ya no existe. */
sealed interface BloqueoEvento {
    object Desbloqueado : BloqueoEvento
    object SesionExpirada : BloqueoEvento
}
