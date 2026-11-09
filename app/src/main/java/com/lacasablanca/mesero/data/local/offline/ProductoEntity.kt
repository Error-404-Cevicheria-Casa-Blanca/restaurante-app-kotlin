package com.lacasablanca.mesero.data.local.offline

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cache local de `GET /api/v1/productos` (PRB-62).
 * `precio` se guarda como texto para no perder precision de BigDecimal.
 */
@Entity(tableName = "productos_cache")
data class ProductoEntity(
    @PrimaryKey val id: Long,
    val nombre: String,
    val descripcion: String,
    val precio: String,
    val disponible: Boolean,
    val categoriaId: Long,
    val imagenUrl: String?,
    val estacion: String?,
    /**
     * PRB-92: modificadores del plato serializados como JSON. Se guardan en
     * texto para conservar los nombres y los `BigDecimal` sin perder
     * precision. La migracion 1->2 agrega la columna con `[]` por defecto.
     */
    val modificadores: String = "[]"
)
