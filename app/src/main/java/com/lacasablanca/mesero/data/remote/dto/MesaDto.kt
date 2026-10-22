package com.lacasablanca.mesero.data.remote.dto

import com.lacasablanca.mesero.domain.model.EstadoMesa
import com.lacasablanca.mesero.domain.model.Mesa

/** DTO de GET /api/v1/mesas. Nombres identicos al JSON (contrato v1). */
data class MesaDto(
    val id: Long? = null,
    val numero: Int? = null,
    val capacidad: Int? = null,
    val ubicacion: String? = null,
    val estado: String? = null
)

/** Cuerpo de PATCH /api/v1/mesas/{id}/estado. */
data class CambioEstadoDto(val estado: String)

/**
 * Mapeo DTO -> dominio.
 * Un estado no reconocido cae en [EstadoMesa.DESCONOCIDO] en vez de lanzar excepcion.
 */
fun MesaDto.aDominio(): Mesa = Mesa(
    id = id ?: 0L,
    numero = numero ?: 0,
    capacidad = capacidad ?: 0,
    ubicacion = ubicacion.orEmpty(),
    estado = EstadoMesa.desdeApi(estado)
)
