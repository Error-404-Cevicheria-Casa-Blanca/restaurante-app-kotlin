package com.lacasablanca.mesero.ui.pedidos

import com.lacasablanca.mesero.domain.model.OrigenPedido
import com.lacasablanca.mesero.domain.model.PedidoActivo

/** Filtros de la pantalla Pedidos. "Para Llevar" incluye DELIVERY. */
enum class FiltroPedidos {
    TODOS,
    MESA,
    PARA_LLEVAR
}

enum class ErrorPedidos {
    RED,
    SERVIDOR,
    SESION_EXPIRADA,
    DESCONOCIDO
}

/**
 * Estado de Pedidos. [pedidos] ya viene sin COBRADO/ANULADO; el filtro de
 * origen se aplica en [pedidosVisibles] para no recargar al cambiar de chip.
 */
data class PedidosUiState(
    val cargando: Boolean = true,
    val pedidos: List<PedidoActivo> = emptyList(),
    val filtro: FiltroPedidos = FiltroPedidos.TODOS,
    val error: ErrorPedidos? = null
) {
    val pedidosVisibles: List<PedidoActivo>
        get() = when (filtro) {
            FiltroPedidos.TODOS -> pedidos
            FiltroPedidos.MESA -> pedidos.filter { it.tipoPedido == OrigenPedido.MESA }
            FiltroPedidos.PARA_LLEVAR -> pedidos.filter { it.tipoPedido != OrigenPedido.MESA }
        }

    val vacio: Boolean
        get() = !cargando && error == null && pedidosVisibles.isEmpty()
}

sealed interface PedidosEvento {
    data class IrACobroPedido(val pedidoId: Long, val clienteNombre: String?) : PedidosEvento

    data object SesionExpirada : PedidosEvento

    data object Actualizados : PedidosEvento
}
