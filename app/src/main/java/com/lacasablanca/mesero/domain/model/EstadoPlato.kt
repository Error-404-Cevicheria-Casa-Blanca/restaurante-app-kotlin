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
        /**
         * Compara sin distinguir mayusculas y tolera espacios alrededor del
         * valor. Un valor nulo o no reconocido cae en [DESCONOCIDO].
         */
        fun desdeApi(valor: String?): EstadoPlato =
            values().firstOrNull { it.valorApi.equals(valor?.trim(), ignoreCase = true) }
                ?: DESCONOCIDO
    }
}
