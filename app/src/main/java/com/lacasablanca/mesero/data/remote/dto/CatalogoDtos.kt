package com.lacasablanca.mesero.data.remote.dto

import com.google.gson.annotations.SerializedName
import com.lacasablanca.mesero.domain.model.Categoria
import com.lacasablanca.mesero.domain.model.Producto
import java.math.BigDecimal

/**
 * GET /api/v1/categorias. El backend Java devuelve `{id, nombre}`.
 */
data class CategoriaDto(
    val id: Long? = null,
    val nombre: String? = null
)

/**
 * GET /api/v1/productos. El backend Java (ProductoDto.java) devuelve:
 * id, nombre, descripcion, precio, disponible, categoria_id, imagen_url, estacion.
 */
data class ProductoDto(
    val id: Long? = null,
    val nombre: String? = null,
    val descripcion: String? = null,
    val precio: BigDecimal? = null,
    val disponible: Boolean? = null,
    @SerializedName("categoria_id") val categoriaId: Long? = null,
    @SerializedName("imagen_url") val imagenUrl: String? = null,
    val estacion: String? = null
)

fun CategoriaDto.aDominio(): Categoria = Categoria(
    id = id ?: 0L,
    nombre = nombre.orEmpty()
)

fun ProductoDto.aDominio(): Producto = Producto(
    id = id ?: 0L,
    nombre = nombre.orEmpty(),
    descripcion = descripcion.orEmpty(),
    precio = precio ?: BigDecimal.ZERO,
    disponible = disponible ?: true,
    categoriaId = categoriaId ?: 0L,
    imagenUrl = imagenUrl,
    estacion = estacion
)
