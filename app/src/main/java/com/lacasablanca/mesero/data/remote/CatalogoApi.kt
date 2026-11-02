package com.lacasablanca.mesero.data.remote

import com.lacasablanca.mesero.data.remote.dto.CategoriaDto
import com.lacasablanca.mesero.data.remote.dto.ProductoDto
import retrofit2.http.GET

/**
 * Contrato v1 del catalogo (PRB-38):
 * GET /api/v1/categorias y GET /api/v1/productos.
 * El backend Java ya expone ambos endpoints (CategoriaController/ProductoController).
 */
interface CatalogoApi {

    @GET("api/v1/categorias")
    suspend fun listarCategorias(): List<CategoriaDto>

    @GET("api/v1/productos")
    suspend fun listarProductos(): List<ProductoDto>
}
