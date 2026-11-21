package com.lacasablanca.mesero.ui.perfil

import com.lacasablanca.mesero.domain.equipo.ErrorTerminal
import com.lacasablanca.mesero.domain.equipo.ErrorUrl

/**
 * Estado del dialogo de Ajustes de equipo (PRB-99). Los errores son enums para
 * que el Fragment los traduzca a `strings.xml`.
 */
data class AjustesEquipoUiState(
    val abierto: Boolean = false,
    val url: String = "",
    val terminal: String = "",
    val guardando: Boolean = false,
    val guardado: Boolean = false,
    val errorUrl: ErrorUrl? = null,
    val errorTerminal: ErrorTerminal? = null,
    val errorGeneral: Boolean = false
)
