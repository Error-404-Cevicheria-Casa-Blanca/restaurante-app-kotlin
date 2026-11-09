package com.lacasablanca.mesero.ui.mesas

import com.lacasablanca.mesero.domain.model.EstadoMesa
import com.lacasablanca.mesero.domain.model.ItemCarrito
import com.lacasablanca.mesero.domain.model.Mesa
import java.math.BigDecimal

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
    ERROR_RED,

    /** PRB-93: la mesa no tiene platos enviados que se puedan dividir. */
    SIN_PLATOS_DIVIDIR,

    /** PRB-93: no hay mesas candidatas para la operacion elegida. */
    SIN_DESTINOS,

    /** PRB-93: 409 de dividir/fusionar/transferir (la mesa cambio de estado). */
    CONFLICTO_ACCION,

    /** PRB-93: fallo generico de una operacion de salon. */
    ERROR_ACCION
}

/** Operaciones de salon de PRB-93. */
enum class AccionMesa {
    DIVIDIR,
    FUSIONAR,
    TRANSFERIR
}

sealed interface MesasEvento {
    /** Mesa LIBRE abierta por este mozo: se entra a la comanda editable. */
    data class IrAComanda(val mesa: Mesa) : MesasEvento

    /** Mesa OCUPADA: se muestra el pedido existente en modo lectura (PRB-83). */
    data class IrAComandaLectura(val mesa: Mesa) : MesasEvento

    /**
     * Mesa ATENDIDA (azul): esta abierta pero sin pedido enviado. La pantalla
     * ofrece retomar la comanda o liberar la mesa (PRB-83).
     */
    data class OpcionesMesaAtendida(val mesa: Mesa) : MesasEvento

    /** El PATCH a LIBRE se confirmo: la pantalla cierra el dialogo de progreso. */
    data class MesaLiberada(val mesaId: Long) : MesasEvento

    /** PRB-93: abrir el menu premium con dividir, fusionar y transferir. */
    data class MostrarAcciones(val mesa: Mesa) : MesasEvento

    /**
     * PRB-93: dialogo de division con las lineas enviadas (solo las que tienen
     * id de detalle) y las mesas que pueden recibir la cuenta nueva.
     */
    data class MostrarDivision(
        val mesa: Mesa,
        val lineas: List<ItemCarrito>,
        val destinos: List<Mesa>
    ) : MesasEvento

    /** PRB-93: dialogo para elegir la mesa destino de fusion o transferencia. */
    data class MostrarDestino(
        val accion: AccionMesa,
        val mesa: Mesa,
        val destinos: List<Mesa>
    ) : MesasEvento

    /**
     * PRB-93: operacion completada. La pantalla muestra el Snackbar con
     * [total] (si el backend lo informa) y [platos] en la division.
     */
    data class AccionMesaCompletada(
        val accion: AccionMesa,
        val mesaOrigen: Mesa,
        val mesaDestino: Mesa?,
        val total: BigDecimal?,
        val platos: Int?
    ) : MesasEvento

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
    val rolUsuario: String = "",
    val error: ErrorMesas? = null,
    val mesaAbriendo: Long? = null,
    /** PRB-93: operacion en curso; bloquea un segundo envio mientras dura. */
    val accionEnCurso: AccionMesa? = null
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
