package com.lacasablanca.mesero.data.repository

import com.lacasablanca.mesero.domain.Resultado
import com.lacasablanca.mesero.domain.model.CuentaMesa
import com.lacasablanca.mesero.domain.model.DivisionMesa
import com.lacasablanca.mesero.domain.model.EstadoMesa
import com.lacasablanca.mesero.domain.model.Mesa

/** Unica fuente de datos del mapa de mesas para la UI. */
interface MesaRepository {
    suspend fun listarMesas(): Resultado<List<Mesa>>
    suspend fun cambiarEstado(id: Long, estado: EstadoMesa): Resultado<Mesa>

    /**
     * PRB-93: separa [itemIds] del pedido activo de la mesa origen.
     * [mesaDestinoId] nulo o igual a la origen crea la cuenta nueva en la
     * misma mesa (comportamiento de `PedidoService.dividirMesa`).
     */
    suspend fun dividirMesa(
        mesaOrigenId: Long,
        itemIds: List<Long>,
        mesaDestinoId: Long?
    ): Resultado<DivisionMesa>

    /**
     * PRB-93: une los pedidos de [mesaOrigenId] en [mesaDestinoId].
     * El backend deja la mesa origen LIBRE.
     */
    suspend fun fusionarMesas(
        mesaDestinoId: Long,
        mesaOrigenId: Long
    ): Resultado<CuentaMesa>

    /**
     * PRB-93: mueve los pedidos de [mesaOrigenId] a [mesaDestinoId] y deja la
     * origen LIBRE. El mozo asignado lo hereda el backend.
     */
    suspend fun transferirMesa(
        mesaOrigenId: Long,
        mesaDestinoId: Long
    ): Resultado<CuentaMesa>
}
