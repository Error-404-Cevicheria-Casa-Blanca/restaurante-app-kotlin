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
 * Una linea visible del carrito. Las lineas `editable = false` son platos ya
 * pedidos en una mesa OCUPADA: se muestran pero no se pueden quitar ni
 * cambiar (PRB-83).
 */
data class LineaCarrito(
    val item: ItemCarrito,
    val editable: Boolean
)

/**
 * Estado unico de la comanda (catalogo + carrito). El desglose se calcula
 * con la misma formula del backend: IGV = Total - Subtotal (D13).
 *
 * En modo lectura (mesa OCUPADA, PRB-83) [itemsExistentes] son los platos ya
 * registrados y [items] solo contiene lo que el mesero agrega para ampliar la
 * comanda; el envio manda unicamente las lineas nuevas.
 */
data class CarritoUiState(
    val cargando: Boolean = true,
    val categorias: List<Categoria> = emptyList(),
    val productos: List<Producto> = emptyList(),
    val filas: List<FilaCatalogo> = emptyList(),
    val categoriaSeleccionada: Long? = null,
    val items: List<ItemCarrito> = emptyList(),
    val errorCatalogo: ErrorCarrito? = null,
    val enviando: Boolean = false,
    val modoLectura: Boolean = false,
    val itemsExistentes: List<ItemCarrito> = emptyList(),
    /** Id del pedido de la mesa; lo exige el endpoint de anulacion (PRB-77). */
    val pedidoExistenteId: Long = 0L,
    /** Linea cuya anulacion esta en vuelo: bloquea dobles toques (PRB-77). */
    val anulandoItemId: Long? = null,
    val cargandoPedidoExistente: Boolean = false,
    val pedidoExistenteCargado: Boolean = false,
    val errorPedidoExistente: ErrorCarrito? = null
) {
    /** Solo hay lineas nuevas (editables) en el carrito. */
    val carritoVacio: Boolean get() = items.isEmpty()

    /** No hay nada que mostrar ni existente ni nuevo. */
    val sinLineas: Boolean get() = itemsExistentes.isEmpty() && items.isEmpty()

    /** Primero los platos ya pedidos (no editables) y luego los nuevos. */
    val lineas: List<LineaCarrito>
        get() = itemsExistentes.map { LineaCarrito(it, editable = false) } +
            items.map { LineaCarrito(it, editable = true) }

    val totalBruto: BigDecimal
        get() = (itemsExistentes + items)
            .fold(BigDecimal.ZERO) { acumulado, item -> acumulado + item.subtotal }
            .setScale(2, RoundingMode.HALF_UP)

    val desglose: DesglosePedido get() = CalculoIgv.desglosar(totalBruto)

    val subtotal: BigDecimal get() = desglose.subtotal
    val igv: BigDecimal get() = desglose.igv
    val total: BigDecimal get() = desglose.total

    val cantidadItems: Int get() = (itemsExistentes + items).sumOf { it.cantidad }

    val catalogoVacio: Boolean
        get() = !cargando && errorCatalogo == null && filas.isEmpty()
}

/** Eventos que la Activity traduce a dialogos o navegacion. */
sealed interface CarritoEvento {
    data class PedidoEnviadoOk(val pedido: PedidoEnviado) : CarritoEvento
    data class ErrorAlEnviar(val error: ErrorApp) : CarritoEvento

    /** PRB-77: el backend confirmo la anulacion y la linea ya no esta. */
    data class ItemAnulado(val nombrePlato: String) : CarritoEvento

    /** PRB-77: no se pudo anular (409 = plato ya servido, red, etc.). */
    data class ErrorAlAnular(val error: ErrorApp) : CarritoEvento

    object SesionExpirada : CarritoEvento
}
