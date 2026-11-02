package com.lacasablanca.mesero.ui.comanda

import com.lacasablanca.mesero.domain.ErrorApp
import com.lacasablanca.mesero.domain.model.CalculoIgv
import com.lacasablanca.mesero.domain.model.Categoria
import com.lacasablanca.mesero.domain.model.DesglosePedido
import com.lacasablanca.mesero.domain.model.ItemCarrito
import com.lacasablanca.mesero.domain.model.PedidoEnviado
import com.lacasablanca.mesero.domain.model.Producto
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Filas del catalogo agrupadas por categoria. El ViewModel arma la lista;
 * el adapter solo pinta (una fila de encabezado por categoria y una por plato).
 */
sealed interface FilaCatalogo {
    /** [nombre] null = categoria desconocida; el adapter usa "Otros". */
    data class Encabezado(
        val categoriaId: Long?,
        val nombre: String?
    ) : FilaCatalogo

    data class Fila(val producto: Producto) : FilaCatalogo
}

/** Error de carga del catalogo, ya traducido para la pantalla. */
enum class ErrorCarrito { RED, SERVIDOR, DESCONOCIDO }

/**
 * Estado unico de la comanda (catalogo + carrito). El desglose se calcula
 * con la misma formula del backend: IGV = Total - Subtotal (D13).
 */
data class CarritoUiState(
    val cargando: Boolean = true,
    val categorias: List<Categoria> = emptyList(),
    val productos: List<Producto> = emptyList(),
    val filas: List<FilaCatalogo> = emptyList(),
    val categoriaSeleccionada: Long? = null,
    val items: List<ItemCarrito> = emptyList(),
    val errorCatalogo: ErrorCarrito? = null,
    val enviando: Boolean = false
) {
    val carritoVacio: Boolean get() = items.isEmpty()

    val totalBruto: BigDecimal
        get() = items
            .fold(BigDecimal.ZERO) { acumulado, item -> acumulado + item.subtotal }
            .setScale(2, RoundingMode.HALF_UP)

    val desglose: DesglosePedido get() = CalculoIgv.desglosar(totalBruto)

    val subtotal: BigDecimal get() = desglose.subtotal
    val igv: BigDecimal get() = desglose.igv
    val total: BigDecimal get() = desglose.total

    val cantidadItems: Int get() = items.sumOf { it.cantidad }

    val catalogoVacio: Boolean
        get() = !cargando && errorCatalogo == null && filas.isEmpty()
}

/** Eventos que la Activity traduce a dialogos o navegacion. */
sealed interface CarritoEvento {
    data class PedidoEnviadoOk(val pedido: PedidoEnviado) : CarritoEvento
    data class ErrorAlEnviar(val error: ErrorApp) : CarritoEvento
    object SesionExpirada : CarritoEvento
}
