package com.lacasablanca.mesero.data.local.offline

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/** Lectura y reemplazo atomico del catalogo cacheado. */
@Dao
abstract class CatalogoDao {

    @Query("SELECT * FROM categorias_cache ORDER BY nombre")
    abstract suspend fun categorias(): List<CategoriaEntity>

    @Query("SELECT * FROM productos_cache ORDER BY nombre")
    abstract suspend fun productos(): List<ProductoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertarCategorias(items: List<CategoriaEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertarProductos(items: List<ProductoEntity>)

    @Query("DELETE FROM categorias_cache")
    abstract suspend fun limpiarCategorias()

    @Query("DELETE FROM productos_cache")
    abstract suspend fun limpiarProductos()

    @Transaction
    open suspend fun reemplazarCategorias(items: List<CategoriaEntity>) {
        limpiarCategorias()
        insertarCategorias(items)
    }

    @Transaction
    open suspend fun reemplazarProductos(items: List<ProductoEntity>) {
        limpiarProductos()
        insertarProductos(items)
    }
}
