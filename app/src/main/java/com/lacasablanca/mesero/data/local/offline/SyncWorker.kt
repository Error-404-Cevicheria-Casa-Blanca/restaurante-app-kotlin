package com.lacasablanca.mesero.data.local.offline

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lacasablanca.mesero.MeseroApp

/**
 * Worker de sincronizacion del outbox (PRB-63). Es una envoltura delgada:
 * toda la logica de envio vive en [PedidoSync], que si tiene tests unitarios.
 */
class SyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val contenedor = (applicationContext as MeseroApp).container
        return try {
            if (contenedor.pedidoSync.sincronizar()) Result.success() else Result.retry()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
