package com.lacasablanca.mesero.data.repository

import com.lacasablanca.mesero.domain.Resultado
import com.lacasablanca.mesero.domain.model.Categoria
import com.lacasablanca.mesero.domain.model.Producto

/** Unica fuente de datos de la carta para la comanda (PRB-38). */
interface CatalogoRepository {
    suspend fun listarCategorias(): Resultado<List<Categoria>>
    suspend fun listarProductos(): Resultado<List<Producto>>
}
