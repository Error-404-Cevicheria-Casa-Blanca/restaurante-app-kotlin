package com.lacasablanca.mesero.ui.common

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.lacasablanca.mesero.R
import com.lacasablanca.mesero.domain.model.EstadoMesa

/**
 * Semantica visual fija de los estados de mesa (DISENO-UI.md).
 * Nunca se comunica el estado solo con color: siempre icono + texto.
 */
@ColorRes
fun EstadoMesa.colorRes(): Int = when (this) {
    EstadoMesa.LIBRE -> R.color.estado_libre
    EstadoMesa.OCUPADA -> R.color.estado_ocupada
    EstadoMesa.CUENTA_SOLICITADA -> R.color.estado_cuenta
    EstadoMesa.ATENDIDA -> R.color.estado_atencion
    EstadoMesa.DESCONOCIDO -> R.color.estado_desconocido
}

@DrawableRes
fun EstadoMesa.iconoRes(): Int = when (this) {
    EstadoMesa.LIBRE -> R.drawable.ic_check
    EstadoMesa.OCUPADA -> R.drawable.ic_restaurant
    EstadoMesa.CUENTA_SOLICITADA -> R.drawable.ic_receipt
    EstadoMesa.ATENDIDA -> R.drawable.ic_edit
    EstadoMesa.DESCONOCIDO -> R.drawable.ic_error_outline
}

@StringRes
fun EstadoMesa.etiquetaRes(): Int = when (this) {
    EstadoMesa.LIBRE -> R.string.estado_libre
    EstadoMesa.OCUPADA -> R.string.estado_ocupada
    EstadoMesa.CUENTA_SOLICITADA -> R.string.estado_cuenta
    EstadoMesa.ATENDIDA -> R.string.estado_atendida
    EstadoMesa.DESCONOCIDO -> R.string.estado_desconocido
}

@StringRes
fun EstadoMesa.etiquetaPluralRes(): Int = when (this) {
    EstadoMesa.LIBRE -> R.string.estado_libre_plural
    EstadoMesa.OCUPADA -> R.string.estado_ocupada_plural
    EstadoMesa.CUENTA_SOLICITADA -> R.string.estado_cuenta_plural
    EstadoMesa.ATENDIDA -> R.string.estado_atendida_plural
    EstadoMesa.DESCONOCIDO -> R.string.estado_desconocido
}
