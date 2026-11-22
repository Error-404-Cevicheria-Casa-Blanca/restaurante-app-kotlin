package com.lacasablanca.mesero.data.local

import kotlinx.coroutines.flow.Flow

/** Valores persistidos de la tablet; `null` significa "sin configurar". */
data class EquipoGuardado(
    val serverUrl: String? = null,
    val terminalCodigo: String? = null
)

/**
 * Almacen persistente de la configuracion de equipo (DataStore en produccion,
 * fake en memoria en los tests).
 */
interface EquipoAlmacen {
    val cambios: Flow<EquipoGuardado>

    suspend fun leer(): EquipoGuardado

    suspend fun guardar(datos: EquipoGuardado)
}
