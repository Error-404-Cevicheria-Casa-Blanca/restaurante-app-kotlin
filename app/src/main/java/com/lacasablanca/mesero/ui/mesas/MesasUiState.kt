package com.lacasablanca.mesero.ui.mesas

import com.lacasablanca.mesero.domain.model.EstadoMesa
import com.lacasablanca.mesero.domain.model.Mesa

/** Errores de carga del mapa de mesas. */
enum class ErrorMesas {
    RED,
    SESION_EXPIRADA,
    SERVIDOR,
    DESCONOCIDO
}

/** Avisos puntuales que la Activity muestra como dialogo Material o snackbar. */
enum class AvisoMesas {
    ACTUALIZADAS,
    MESA_NO_LIBRE,
    MESA_TOMADA,
    ERROR_ABRIR,
    ERROR_LIBERAR,
    ERROR_RED
}

sealed interface MesasEvento {
    data class IrAComanda(val mesa: Mesa) : MesasEvento
    data class Aviso(val tipo: AvisoMesas, val mesa: Mesa? = null) : MesasEvento
    object SesionExpirada : MesasEvento
    object SesionCerrada : MesasEvento
}

data class MesasUiState(
    val cargando: Boolean = true,
    val mesas: List<Mesa> = emptyList(),
    val zonas: List<String> = emptyList(),
    val zonaSeleccionada: String? = null,
    val estadoSeleccionado: EstadoMesa? = null,
    val nombreUsuario: String = "",
    val error: ErrorMesas? = null,
    val mesaAbriendo: Long? = null
) {
    /** Mesas de la zona elegida (base para contadores y chips). */
    val mesasDeZona: List<Mesa>
        get() = mesas.filter { zonaSeleccionada == null || it.ubicacion == zonaSeleccionada }

    /** Mesas que se pintan en la cuadricula (zona + estado). */
    val mesasVisibles: List<Mesa>
        get() = mesasDeZona.filter { estadoSeleccionado == null || it.estado == estadoSeleccionado }

    /** Contadores por estado calculados sobre la zona activa. */
    val contadores: Map<EstadoMesa, Int>
        get() = EstadoMesa.filtrables.associateWith { estado ->
            mesasDeZona.count { it.estado == estado }
        }

    val totalMesas: Int get() = mesas.size

    val sinMesas: Boolean get() = !cargando && error == null && mesas.isEmpty()

    val sinResultados: Boolean
        get() = !cargando && error == null && mesas.isNotEmpty() && mesasVisibles.isEmpty()
}
