package com.lacasablanca.mesero.data.local.offline

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Base local unica de la app: cache de catalogo/mesas + outbox de pedidos. */
@Database(
    entities = [
        ProductoEntity::class,
        CategoriaEntity::class,
        MesaEntity::class,
        PedidoPendienteEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun catalogoDao(): CatalogoDao
    abstract fun mesaDao(): MesaDao
    abstract fun pedidoPendienteDao(): PedidoPendienteDao

    companion object {
        private const val NOMBRE = "casa-blanca-offline.db"

        /**
         * PRB-92: agrega la lista de modificadores a la cache de productos.
         * Un catalogo viejo se queda con `[]` y se vuelve a llenar en la
         * siguiente consulta con red.
         */
        val MIGRACION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE productos_cache " +
                        "ADD COLUMN modificadores TEXT NOT NULL DEFAULT '[]'"
                )
            }
        }

        fun crear(contexto: Context): AppDatabase = Room.databaseBuilder(
            contexto.applicationContext,
            AppDatabase::class.java,
            NOMBRE
        )
            .addMigrations(MIGRACION_1_2)
            .build()
    }
}
