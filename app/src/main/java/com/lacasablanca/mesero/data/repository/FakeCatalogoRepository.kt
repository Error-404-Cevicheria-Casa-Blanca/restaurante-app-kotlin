package com.lacasablanca.mesero.data.repository

import com.lacasablanca.mesero.domain.Resultado
import com.lacasablanca.mesero.domain.model.Categoria
import com.lacasablanca.mesero.domain.model.Producto
import kotlinx.coroutines.delay
import java.math.BigDecimal

/**
 * DATOS FAKE (no reales), rotulados en pantalla como "Datos de demostración".
 * Reproduce la carta sembrada por el backend (DataInitializer) para que la app
 * funcione sin servidor cuando BuildConfig.USE_FAKE_DATA = true.
 */
class FakeCatalogoRepository(
    private val latenciaMs: Long = LATENCIA_SIMULADA_MS
) : CatalogoRepository {

    override suspend fun listarCategorias(): Resultado<List<Categoria>> {
        delay(latenciaMs)
        return Resultado.Exito(CATEGORIAS_FAKE)
    }

    override suspend fun listarProductos(): Resultado<List<Producto>> {
        delay(latenciaMs)
        return Resultado.Exito(PRODUCTOS_FAKE)
    }

    companion object {
        const val LATENCIA_SIMULADA_MS = 350L

        /** Categorias del modelo de datos (PE-8): 4 categorias. */
        val CATEGORIAS_FAKE: List<Categoria> = listOf(
            Categoria(1, "Entradas"),
            Categoria(2, "Platos de Fondo"),
            Categoria(3, "Bebidas"),
            Categoria(4, "Postres")
        )

        /** Platos de ejemplo; uno no disponible para probar el estado deshabilitado. */
        val PRODUCTOS_FAKE: List<Producto> = listOf(
            Producto(
                id = 10,
                nombre = "Ceviche Clásico La Casa Blanca",
                descripcion = "Pescado del día, camote glaseado y choclo tierno.",
                precio = BigDecimal("48.00"),
                disponible = true,
                categoriaId = 2,
                imagenUrl = null,
                estacion = "BARRA_FRIA"
            ),
            Producto(
                id = 11,
                nombre = "Tiradito al Ají Amarillo",
                descripcion = "Láminas de corvina, crema de ají amarillo y canchita.",
                precio = BigDecimal("46.00"),
                disponible = true,
                categoriaId = 1,
                imagenUrl = null,
                estacion = "BARRA_FRIA"
            ),
            Producto(
                id = 12,
                nombre = "Chicharrón de Calamar",
                descripcion = "Crujientes aros de calamar con salsa criolla.",
                precio = BigDecimal("35.00"),
                disponible = true,
                categoriaId = 1,
                imagenUrl = null,
                estacion = "COCINA_CALIENTE"
            ),
            Producto(
                id = 13,
                nombre = "Chicha Morada 1L",
                descripcion = "Bebida tradicional de maíz morado.",
                precio = BigDecimal("15.00"),
                disponible = true,
                categoriaId = 3,
                imagenUrl = null,
                estacion = "BEBIDAS"
            ),
            Producto(
                id = 14,
                nombre = "Suspiro a la Limeña",
                descripcion = "Postre tradicional con merengue.",
                precio = BigDecimal("18.00"),
                disponible = false,
                categoriaId = 4,
                imagenUrl = null,
                estacion = "POSTRES"
            )
        )
    }
}
