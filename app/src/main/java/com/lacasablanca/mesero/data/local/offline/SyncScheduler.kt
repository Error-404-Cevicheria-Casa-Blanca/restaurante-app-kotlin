package com.lacasablanca.mesero.data.local.offline

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Programa el envio de pedidos pendientes (PRB-63):
 * - un trabajo periodico cada 15 min (minimo de WorkManager) con red;
 * - un intento inmediato al abrir la app, por si ya hay conexion.
 */
object SyncScheduler {

    private const val TRABAJO = "sync-pedidos-pendientes"
    private const val TRABAJO_AHORA = "$TRABAJO-ahora"
    private const val MINUTOS_PERIODICO = 15L
    private const val SEGUNDOS_BACKOFF = 30L

    fun programar(contexto: Context) {
        val restricciones = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val periodico = PeriodicWorkRequestBuilder<SyncWorker>(
            MINUTOS_PERIODICO,
            TimeUnit.MINUTES
        )
            .setConstraints(restricciones)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                SEGUNDOS_BACKOFF,
                TimeUnit.SECONDS
            )
            .build()

        val ahora = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(restricciones)
            .build()

        val gestor = WorkManager.getInstance(contexto)
        gestor.enqueueUniquePeriodicWork(
            TRABAJO,
            ExistingPeriodicWorkPolicy.KEEP,
            periodico
        )
        gestor.enqueueUniqueWork(TRABAJO_AHORA, ExistingWorkPolicy.KEEP, ahora)
    }
}
