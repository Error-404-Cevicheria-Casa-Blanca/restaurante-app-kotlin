package com.lacasablanca.mesero.ui.common

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.lacasablanca.mesero.R
import com.lacasablanca.mesero.domain.model.EstadoMesa
import com.lacasablanca.mesero.domain.model.EstadoPlato

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

/*
 * PRB-76: semantica visual de los estados de preparacion del plato.
 * PENDIENTE azul claro, EN_PREPARACION naranja, LISTO verde, SERVIDO teal y
 * ANULADO gris. La pildora siempre lleva texto: el color no es el unico canal.
 */
@StringRes
fun EstadoPlato.etiquetaRes(): Int = when (this) {
    EstadoPlato.PENDIENTE -> R.string.estado_plato_pendiente
    EstadoPlato.EN_PREPARACION -> R.string.estado_plato_preparacion
    EstadoPlato.LISTO -> R.string.estado_plato_listo
    EstadoPlato.SERVIDO -> R.string.estado_plato_servido
    EstadoPlato.ANULADO -> R.string.estado_plato_anulado
    EstadoPlato.DESCONOCIDO -> R.string.estado_plato_desconocido
}

@ColorRes
fun EstadoPlato.colorFondoRes(): Int = when (this) {
    EstadoPlato.PENDIENTE -> R.color.estado_plato_pendiente_fondo
    EstadoPlato.EN_PREPARACION -> R.color.estado_plato_preparacion_fondo
    EstadoPlato.LISTO -> R.color.estado_plato_listo_fondo
    EstadoPlato.SERVIDO -> R.color.estado_plato_servido_fondo
    EstadoPlato.ANULADO -> R.color.estado_plato_anulado_fondo
    EstadoPlato.DESCONOCIDO -> R.color.estado_plato_desconocido_fondo
}

@ColorRes
fun EstadoPlato.colorTextoRes(): Int = when (this) {
    EstadoPlato.PENDIENTE -> R.color.estado_plato_pendiente_texto
    EstadoPlato.EN_PREPARACION -> R.color.estado_plato_preparacion_texto
    EstadoPlato.LISTO -> R.color.estado_plato_listo_texto
    EstadoPlato.SERVIDO -> R.color.estado_plato_servido_texto
    EstadoPlato.ANULADO -> R.color.estado_plato_anulado_texto
    EstadoPlato.DESCONOCIDO -> R.color.estado_plato_desconocido_texto
}
