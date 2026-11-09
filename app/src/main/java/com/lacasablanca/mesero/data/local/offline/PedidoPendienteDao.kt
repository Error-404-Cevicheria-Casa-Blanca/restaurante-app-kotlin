package com.lacasablanca.mesero.data.local.offline

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** Cola de salida (outbox) de pedidos creados sin conexion. */
@Dao
interface PedidoPendienteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertar(pedido: PedidoPendienteEntity): Long

    @Query("SELECT * FROM pedidos_pendientes ORDER BY creado_en ASC")
    suspend fun pendientes(): List<PedidoPendienteEntity>

    @Query("DELETE FROM pedidos_pendientes WHERE id = :id")
    suspend fun eliminar(id: Long)

    @Query(
        "UPDATE pedidos_pendientes SET intentos = intentos + 1, " +
            "ultimo_error = :error WHERE id = :id"
    )
    suspend fun registrarFallo(id: Long, error: String?)

    @Query("SELECT COUNT(*) FROM pedidos_pendientes")
    suspend fun contar(): Int
}
