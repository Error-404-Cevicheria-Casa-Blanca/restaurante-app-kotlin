package com.lacasablanca.mesero.data.repository

import com.lacasablanca.mesero.data.remote.CatalogoApi
import com.lacasablanca.mesero.data.remote.dto.aDominio
import com.lacasablanca.mesero.domain.ErrorApp
import com.lacasablanca.mesero.domain.Resultado
import com.lacasablanca.mesero.domain.errorDesdeCodigoHttp
import com.lacasablanca.mesero.domain.model.Categoria
import com.lacasablanca.mesero.domain.model.Producto
import retrofit2.HttpException
import java.io.IOException

/** Catalogo contra la API real (GET /categorias y GET /productos). */
class RealCatalogoRepository(private val api: CatalogoApi) : CatalogoRepository {

    override suspend fun listarCategorias(): Resultado<List<Categoria>> {
        return try {
            Resultado.Exito(api.listarCategorias().map { it.aDominio() })
        } catch (e: HttpException) {
            Resultado.Fallo(errorDesdeCodigoHttp(e.code()))
        } catch (e: IOException) {
            Resultado.Fallo(ErrorApp.RED)
        } catch (e: Exception) {
            Resultado.Fallo(ErrorApp.DESCONOCIDO)
        }
    }

    override suspend fun listarProductos(): Resultado<List<Producto>> {
        return try {
            Resultado.Exito(api.listarProductos().map { it.aDominio() })
        } catch (e: HttpException) {
            Resultado.Fallo(errorDesdeCodigoHttp(e.code()))
        } catch (e: IOException) {
            Resultado.Fallo(ErrorApp.RED)
        } catch (e: Exception) {
            Resultado.Fallo(ErrorApp.DESCONOCIDO)
        }
    }
}
