package com.lacasablanca.mesero.domain.model

import java.math.BigDecimal

/**
 * Categoria de la carta (GET /api/v1/categorias).
 * Campos del backend Java: id, nombre.
 */
data class Categoria(
    val id: Long,
    val nombre: String
)

/**
 * Plato de la carta (GET /api/v1/productos).
 * `precio` INCLUYE IGV (D13); el desglose se calcula en [CalculoIgv].
 * `estacion` llega del backend (BARRA_FRIA, COCINA_CALIENTE, BEBIDAS, POSTRES)
 * y se conserva para las historias de division por estaciones.
 */
data class Producto(
    val id: Long,
    val nombre: String,
    val descripcion: String,
    val precio: BigDecimal,
    val disponible: Boolean,
    val categoriaId: Long,
    val imagenUrl: String?,
    val estacion: String?
) {
    val sinImagenRemota: Boolean get() = imagenUrl.isNullOrBlank()
}
