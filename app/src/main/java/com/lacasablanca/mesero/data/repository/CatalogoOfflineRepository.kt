package com.lacasablanca.mesero.data.repository

import com.lacasablanca.mesero.data.local.offline.CatalogoDao
import com.lacasablanca.mesero.data.local.offline.aDominio
import com.lacasablanca.mesero.data.local.offline.aEntidad
import com.lacasablanca.mesero.domain.ErrorApp
import com.lacasablanca.mesero.domain.Resultado
import com.lacasablanca.mesero.domain.model.Categoria
import com.lacasablanca.mesero.domain.model.Producto

/**
 * Cachea el catalogo en Room (PRB-62). Con red devuelve la respuesta del
 * backend y actualiza la cache; sin red devuelve la ultima copia guardada.
 * Si no hay red ni cache, conserva el error de red para que la UI reintente.
 */
class CatalogoOfflineRepository(
    private val remoto: CatalogoRepository,
    private val dao: CatalogoDao
) : CatalogoRepository {

    override suspend fun listarCategorias(): Resultado<List<Categoria>> {
        return when (val resultado = remoto.listarCategorias()) {
            is Resultado.Exito -> {
                dao.reemplazarCategorias(resultado.datos.map { it.aEntidad() })
                resultado
            }

            is Resultado.Fallo -> {
                if (resultado.error != ErrorApp.RED) return resultado
                val cache = dao.categorias().map { it.aDominio() }
                if (cache.isEmpty()) resultado else Resultado.Exito(cache)
            }
        }
    }

    override suspend fun listarProductos(): Resultado<List<Producto>> {
        return when (val resultado = remoto.listarProductos()) {
            is Resultado.Exito -> {
                dao.reemplazarProductos(resultado.datos.map { it.aEntidad() })
                resultado
            }

            is Resultado.Fallo -> {
                if (resultado.error != ErrorApp.RED) return resultado
                val cache = dao.productos().map { it.aDominio() }
                if (cache.isEmpty()) resultado else Resultado.Exito(cache)
            }
        }
    }
}
