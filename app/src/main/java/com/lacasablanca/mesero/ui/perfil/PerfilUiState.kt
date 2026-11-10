package com.lacasablanca.mesero.ui.perfil

/**
 * Estado del perfil del mesero (PRB-88, Fase 3).
 *
 * Los campos que el backend aun no entrega (terminal y turno) se dejan vacios
 * para que el fragmento los rotule con "No asignado" desde strings.xml.
 */
data class PerfilUiState(
    val cargando: Boolean = true,
    val nombre: String = "",
    val rol: String = "",
    val usuario: String = "",
    val terminal: String = "",
    val turno: String = "",
    val fecha: String = "",
    val cerrandoSesion: Boolean = false
)

/** Eventos de una sola vez que la pantalla traduce a navegacion. */
sealed interface PerfilEvento {
    /** No hay sesion persistida: hay que volver al login. */
    data object IrALogin : PerfilEvento

    /** El usuario cerro sesion desde el perfil. */
    data object SesionCerrada : PerfilEvento
}
