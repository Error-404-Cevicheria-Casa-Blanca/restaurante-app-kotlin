package com.lacasablanca.mesero.data.remote.dto

import com.google.gson.annotations.SerializedName
import com.lacasablanca.mesero.domain.model.Categoria
import com.lacasablanca.mesero.domain.model.Modificador
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
 * Modificador de un plato (PRB-92). Mismos nombres que
 * `ModificadorItemDto.java` del backend: `nombre`, `precio_adicional`.
 *
 * El `GET /productos` de `develop` todavia no envia esta lista; el DTO la
 * acepta de forma tolerante para que solo haya que ajustar el backend cuando
 * Max publique los modificadores disponibles por plato.
 */
data class ModificadorDto(
    val nombre: String? = null,
    @SerializedName("precio_adicional") val precioAdicional: BigDecimal? = null
)

/**
 * GET /api/v1/productos. El backend Java (ProductoDto.java) devuelve:
 * id, nombre, descripcion, precio, disponible, categoria_id, imagen_url, estacion.
 * PRB-92 agrega `modificadores` de forma opcional.
 */
data class ProductoDto(
    val id: Long? = null,
    val nombre: String? = null,
    val descripcion: String? = null,
    val precio: BigDecimal? = null,
    val disponible: Boolean? = null,
    @SerializedName("categoria_id") val categoriaId: Long? = null,
    @SerializedName("imagen_url") val imagenUrl: String? = null,
    val estacion: String? = null,
    val modificadores: List<ModificadorDto>? = null
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
    estacion = estacion,
    modificadores = modificadores.orEmpty().mapNotNull { it.aDominio() }
)

/** Un modificador sin nombre util se descarta para no pintar filas vacias. */
fun ModificadorDto.aDominio(): Modificador? {
    val nombreLimpio = nombre?.trim().orEmpty()
    if (nombreLimpio.isEmpty()) return null
    return Modificador(
        nombre = nombreLimpio,
        precioAdicional = precioAdicional ?: BigDecimal.ZERO
    )
}
