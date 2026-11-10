package com.lacasablanca.mesero.ui.comanda

import android.content.Context
import com.lacasablanca.mesero.R
import com.lacasablanca.mesero.domain.ErrorApp
import com.lacasablanca.mesero.ui.impresion.AvisoImpresion

/**
 * Textos de error y avisos de la comanda. Se mantienen fuera de
 * [ComandaActivity] para que la Activity solo dibuje y reenvie eventos.
 *
 * El ViewModel no tiene `Context`: las claves de error viajan hasta aqui y se
 * traducen con `strings.xml`.
 */
internal object ComandaMensajes {

    fun errorAnular(context: Context, error: ErrorApp): String = when (error) {
        // 409 del endpoint de anulacion: el plato ya salio de cocina.
        ErrorApp.PLATO_YA_SERVIDO -> context.getString(R.string.anular_plato_error_servido)
        ErrorApp.RED -> context.getString(R.string.anular_plato_error_red)
        else -> context.getString(R.string.anular_plato_error_generico)
    }

    fun errorEnvio(context: Context, error: ErrorApp): String = when (error) {
        ErrorApp.RED -> context.getString(R.string.comanda_error_envio_red)
        // 409 del POST /pedidos: otro mesero tomo la mesa. El 409 del cobro
        // ("cuenta ya pagada") se traduce en CobroViewModel, no aqui.
        ErrorApp.CONFLICTO, ErrorApp.CONFLICTO_MESA ->
            context.getString(R.string.comanda_error_envio_conflicto)

        ErrorApp.BLOQUEADO -> context.getString(R.string.comanda_error_envio_bloqueado)
        ErrorApp.VALIDACION -> context.getString(R.string.comanda_error_envio_validacion)
        else -> context.getString(R.string.comanda_error_envio_generico)
    }

    fun errorCuenta(context: Context, error: ErrorApp): String = when (error) {
        ErrorApp.RED -> context.getString(R.string.error_red)
        ErrorApp.CONFLICTO, ErrorApp.CONFLICTO_MESA ->
            context.getString(R.string.comanda_cuenta_error_conflicto)

        ErrorApp.BLOQUEADO -> context.getString(R.string.comanda_cuenta_error_bloqueado)
        else -> context.getString(R.string.comanda_cuenta_error_generico)
    }

    fun avisoImpresion(context: Context, aviso: AvisoImpresion): String = when (aviso) {
        AvisoImpresion.ENVIADA -> context.getString(R.string.impresion_enviada)
        AvisoImpresion.ERROR_CONEXION -> context.getString(R.string.impresion_error_conexion)
        AvisoImpresion.SESION_EXPIRADA -> context.getString(R.string.impresion_sesion_expirada)
        AvisoImpresion.ERROR -> context.getString(R.string.impresion_error)
    }
}
