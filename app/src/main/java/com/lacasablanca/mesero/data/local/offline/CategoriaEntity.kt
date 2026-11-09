package com.lacasablanca.mesero.data.local.offline

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Cache local de `GET /api/v1/categorias` para poder pintar la carta offline. */
@Entity(tableName = "categorias_cache")
data class CategoriaEntity(
    @PrimaryKey val id: Long,
    val nombre: String
)
