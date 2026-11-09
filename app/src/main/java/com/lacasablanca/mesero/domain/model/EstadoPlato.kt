package com.lacasablanca.mesero.domain.model

/**
 * Estado de preparacion de un plato dentro de una comanda (RF-09, PRB-76).
 *
 * [valorApi] es EXACTAMENTE el que viaja en el JSON del backend; el contrato
 * de `GET /api/v1/pedidos?mesa_id=X` devuelve uno de estos cinco valores.
 */
enum class EstadoPlato(val valorApi: String) {
    PENDIENTE("PENDIENTE"),
    EN_PREPARACION("EN_PREPARACION"),
    LISTO("LISTO"),
    SERVIDO("SERVIDO"),
    ANULADO("ANULADO"),

    /**
     * Solo defensivo: un estado nuevo del backend no debe romper la comanda.
     * No es un estado de negocio y no se usa para filtrar.
     */
    DESCONOCIDO("DESCONOCIDO");

    companion object {
        /** Alias que usa el backend Java para "en preparacion" (PRB-80). */
        private const val ALIAS_EN_PREPARACION = "COCINANDO"

        /**
         * Compara sin distinguir mayusculas y tolera espacios alrededor del
         * valor. `COCINANDO` se acepta como alias de [EN_PREPARACION]; un valor
         * nulo o no reconocido cae en [DESCONOCIDO].
         */
        fun desdeApi(valor: String?): EstadoPlato {
            val limpio = valor?.trim()
            if (limpio.equals(ALIAS_EN_PREPARACION, ignoreCase = true)) {
                return EN_PREPARACION
            }
            return values().firstOrNull { it.valorApi.equals(limpio, ignoreCase = true) }
                ?: DESCONOCIDO
        }
    }
}
