package com.lacasablanca.mesero.data.local.offline

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Cache local de `GET /api/v1/mesas` (PRB-62). */
@Entity(tableName = "mesas_cache")
data class MesaEntity(
    @PrimaryKey val id: Long,
    val numero: Int,
    val capacidad: Int,
    val ubicacion: String,
    val estado: String
)
