package com.lacasablanca.mesero.data.local.offline

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Outbox local de pedidos (PRB-63). `payload` guarda el `CrearPedidoRequest`
 * serializado; `client_request_id` es unico para no encolar dos veces el mismo
 * intento cuando el mesero reintenta sin red.
 */
@Entity(
    tableName = "pedidos_pendientes",
    indices = [Index(value = ["client_request_id"], unique = true)]
)
data class PedidoPendienteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "client_request_id") val clientRequestId: String,
    @ColumnInfo(name = "mesa_id") val mesaId: Long,
    val payload: String,
    @ColumnInfo(name = "creado_en") val creadoEn: Long,
    val intentos: Int = 0,
    @ColumnInfo(name = "ultimo_error") val ultimoError: String? = null
)
