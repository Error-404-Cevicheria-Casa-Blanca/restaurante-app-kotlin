package com.lacasablanca.mesero.ui.comanda

import com.lacasablanca.mesero.domain.ErrorApp

/**
 * Estado de la pantalla de comanda.
 * [liberandoMesa] se pinta deshabilitando la salida mientras corre el PATCH.
 */
data class ComandaUiState(
    val liberandoMesa: Boolean = false
)

/** Eventos que la Activity traduce a navegacion o dialogos. */
sealed interface ComandaEvento {
    /** El PATCH LIBRE termino bien: se puede cerrar la pantalla. */
    object MesaLiberada : ComandaEvento

    /** No se pudo liberar la mesa; la Activity ofrece reintentar o salir. */
    data class ErrorAlLiberar(val error: ErrorApp) : ComandaEvento

    /** El backend respondio 401/403: hay que volver al login. */
    object SesionExpirada : ComandaEvento
}
