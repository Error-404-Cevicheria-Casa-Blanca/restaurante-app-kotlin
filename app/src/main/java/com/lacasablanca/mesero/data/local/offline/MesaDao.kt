package com.lacasablanca.mesero.data.local.offline

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/** Lectura y reemplazo atomico del mapa de mesas cacheado. */
@Dao
abstract class MesaDao {

    @Query("SELECT * FROM mesas_cache ORDER BY numero")
    abstract suspend fun mesas(): List<MesaEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertar(items: List<MesaEntity>)

    @Query("DELETE FROM mesas_cache")
    abstract suspend fun limpiar()

    @Query("UPDATE mesas_cache SET estado = :estado WHERE id = :id")
    abstract suspend fun actualizarEstado(id: Long, estado: String)

    @Transaction
    open suspend fun reemplazar(items: List<MesaEntity>) {
        limpiar()
        insertar(items)
    }
}
