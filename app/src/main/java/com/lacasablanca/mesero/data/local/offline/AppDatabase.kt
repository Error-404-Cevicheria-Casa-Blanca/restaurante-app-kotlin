package com.lacasablanca.mesero.data.local.offline

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/** Base local unica de la app: cache de catalogo/mesas + outbox de pedidos. */
@Database(
    entities = [
        ProductoEntity::class,
        CategoriaEntity::class,
        MesaEntity::class,
        PedidoPendienteEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun catalogoDao(): CatalogoDao
    abstract fun mesaDao(): MesaDao
    abstract fun pedidoPendienteDao(): PedidoPendienteDao

    companion object {
        private const val NOMBRE = "casa-blanca-offline.db"

        fun crear(contexto: Context): AppDatabase = Room.databaseBuilder(
            contexto.applicationContext,
            AppDatabase::class.java,
            NOMBRE
        ).build()
    }
}
