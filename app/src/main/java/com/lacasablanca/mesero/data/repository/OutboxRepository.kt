package com.lacasablanca.mesero.data.repository

import com.lacasablanca.mesero.data.local.offline.OutboxPedidoStore
import com.lacasablanca.mesero.data.local.offline.PedidoSync
import com.lacasablanca.mesero.data.local.offline.aPendiente
import com.lacasablanca.mesero.domain.model.ResumenOutbox

interface OutboxRepository {
    suspend fun resumen(): ResumenOutbox
    suspend fun sincronizar(): Boolean
    suspend fun descartarConflictos()
}

class DefaultOutboxRepository(
    private val store: OutboxPedidoStore,
    private val sync: PedidoSync
) : OutboxRepository {

    override suspend fun resumen(): ResumenOutbox =
        ResumenOutbox(store.pendientes().map { it.aPendiente() })

    override suspend fun sincronizar(): Boolean = sync.sincronizar()

    override suspend fun descartarConflictos() {
        store.pendientes()
            .map { it.aPendiente() }
            .filter { it.conflicto }
            .forEach { store.eliminar(it.id) }
    }
}
