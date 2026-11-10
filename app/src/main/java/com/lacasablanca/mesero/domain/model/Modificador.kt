package com.lacasablanca.mesero.domain.model

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Modificador de un plato (PRB-92): un extra o una preferencia del comensal,
 * por ejemplo "Extra queso" (+S/ 2.00) o "Sin cebolla" (+S/ 0.00).
 *
 * El backend Java (`PedidoService.calcularRecargoModificadores`) suma los
 * precios adicionales y los aplica AL PRECIO UNITARIO antes de multiplicar por
 * la cantidad. Esta clase reproduce esa misma regla con BigDecimal para que el
 * subtotal del carrito coincida con el que calcula el servidor.
 */
data class Modificador(
    val nombre: String,
    val precioAdicional: BigDecimal = BigDecimal.ZERO
) {
    /** true cuando no recarga dinero (preferencia del comensal). */
    val esGratuito: Boolean get() = precioAdicional.compareTo(BigDecimal.ZERO) <= 0

    companion object {
        /**
         * Suma de los recargos de una seleccion. Un precio negativo nunca
         * descuenta: el backend lo rechaza y aqui se ignora por seguridad.
         */
        fun recargoTotal(modificadores: List<Modificador>): BigDecimal =
            modificadores.fold(BigDecimal.ZERO) { total, modificador ->
                total + modificador.precioAdicional.max(BigDecimal.ZERO)
            }.setScale(2, RoundingMode.HALF_UP)
    }
}
