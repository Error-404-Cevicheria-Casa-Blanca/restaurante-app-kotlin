package com.lacasablanca.mesero.ui.impresion

import android.content.Context
import com.lacasablanca.mesero.BuildConfig
import com.lacasablanca.mesero.R

/**
 * Texto del estado real de la cola de impresion (PRB-109). Se comparte entre
 * Cobro y Comanda para no duplicar la regla: IMPRESO solo se pinta cuando la
 * cola lo confirma.
 */
internal object ImpresionTextos {

    fun estado(context: Context, estado: ImpresionUiState): String? {
        val texto = when {
            estado.estadoCola == EstadoColaImpresion.FALLIDO -> textoFallido(context, estado)
            estado.estadoCola == EstadoColaImpresion.IMPRESO ->
                context.getString(R.string.impresion_estado_impreso)

            estado.estadoCola == EstadoColaImpresion.DESCONOCIDO ->
                context.getString(R.string.impresion_estado_desconocido)

            estado.sinConfirmacion ->
                context.getString(R.string.impresion_estado_sin_confirmacion)

            estado.estadoCola == EstadoColaImpresion.PENDIENTE ->
                context.getString(R.string.impresion_estado_en_cola)

            else -> null
        } ?: return null

        return if (BuildConfig.USE_FAKE_DATA) {
            context.getString(R.string.impresion_estado_con_demo, texto)
        } else {
            texto
        }
    }

    private fun textoFallido(context: Context, estado: ImpresionUiState): String {
        val mensaje = estado.mensajeError?.takeIf { it.isNotBlank() }
        return if (mensaje == null) {
            context.getString(R.string.impresion_estado_fallido_sin_mensaje)
        } else {
            context.getString(R.string.impresion_estado_fallido, mensaje)
        }
    }
}
