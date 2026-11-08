package com.lacasablanca.mesero.ui.comanda

import com.lacasablanca.mesero.domain.ErrorApp

/**
 * Estado de la pantalla de comanda.
 * [liberandoMesa] se pinta deshabilitando la salida mientras corre el PATCH.
 * [solicitandoCuenta] hace lo mismo con el boton "Pedir Cuenta" (PRB-83).
 */
data class ComandaUiState(
    val liberandoMesa: Boolean = false,
    val solicitandoCuenta: Boolean = false
)

/** Eventos que la Activity traduce a navegacion o dialogos. */
sealed interface ComandaEvento {
    /** El PATCH LIBRE termino bien: se puede cerrar la pantalla. */
    object MesaLiberada : ComandaEvento

    /** No se pudo liberar la mesa; la Activity ofrece reintentar o salir. */
    data class ErrorAlLiberar(val error: ErrorApp) : ComandaEvento

    /** El PATCH CUENTA_SOLICITADA termino bien: avisar a caja y volver al mapa. */
    object CuentaSolicitada : ComandaEvento

    /** No se pudo pedir la cuenta; la Activity ofrece reintentar. */
    data class ErrorAlPedirCuenta(val error: ErrorApp) : ComandaEvento

    /** El backend respondio 401/403: hay que volver al login. */
    object SesionExpirada : ComandaEvento
}
