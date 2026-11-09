package com.lacasablanca.mesero.data.local.offline

import com.lacasablanca.mesero.domain.model.Categoria
import com.lacasablanca.mesero.domain.model.EstadoMesa
import com.lacasablanca.mesero.domain.model.Mesa
import com.lacasablanca.mesero.domain.model.Producto
import java.math.BigDecimal

fun Producto.aEntidad(): ProductoEntity = ProductoEntity(
    id = id,
    nombre = nombre,
    descripcion = descripcion,
    precio = precio.toPlainString(),
    disponible = disponible,
    categoriaId = categoriaId,
    imagenUrl = imagenUrl,
    estacion = estacion
)

fun ProductoEntity.aDominio(): Producto = Producto(
    id = id,
    nombre = nombre,
    descripcion = descripcion,
    precio = precio.aBigDecimalSeguro(),
    disponible = disponible,
    categoriaId = categoriaId,
    imagenUrl = imagenUrl,
    estacion = estacion
)

fun Categoria.aEntidad(): CategoriaEntity = CategoriaEntity(id = id, nombre = nombre)

fun CategoriaEntity.aDominio(): Categoria = Categoria(id = id, nombre = nombre)

fun Mesa.aEntidad(): MesaEntity = MesaEntity(
    id = id,
    numero = numero,
    capacidad = capacidad,
    ubicacion = ubicacion,
    estado = estado.valorApi
)

fun MesaEntity.aDominio(): Mesa = Mesa(
    id = id,
    numero = numero,
    capacidad = capacidad,
    ubicacion = ubicacion,
    estado = EstadoMesa.desdeApi(estado)
)

private fun String.aBigDecimalSeguro(): BigDecimal = try {
    BigDecimal(this)
} catch (e: NumberFormatException) {
    BigDecimal.ZERO
}
