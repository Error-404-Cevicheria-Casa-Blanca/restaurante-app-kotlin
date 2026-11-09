package com.lacasablanca.mesero.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Trabajo de impresion devuelto por `POST /api/v1/mesas/{id}/precuenta/reimprimir`
 * (PRB-73). Los nombres JSON los define el backend Java en snake_case.
 */
data class TrabajoImpresionDto(
    @SerializedName("job_id")
    val jobId: String? = null,
    @SerializedName("mesa_id")
    val mesaId: Long? = null,
    val tipo: String? = null,
    val estado: String? = null,
    val intentos: Int? = null,
    @SerializedName("mensaje_error")
    val mensajeError: String? = null,
    @SerializedName("fecha_solicitud")
    val fechaSolicitud: String? = null
)
