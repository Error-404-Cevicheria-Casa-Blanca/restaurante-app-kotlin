package com.lacasablanca.mesero.domain.model

/**
 * Estados de mesa fijados por el equipo (D2 / D3).
 * El valor [valorApi] es EXACTAMENTE el que viaja en el JSON del contrato v1.
 */
enum class EstadoMesa(val valorApi: String) {
    LIBRE("LIBRE"),
    OCUPADA("OCUPADA"),
    ATENDIDA("ATENDIDA"),
    CUENTA_SOLICITADA("CUENTA_SOLICITADA"),

    /**
     * Solo defensivo: si el backend envia un estado que la app no conoce,
     * la pantalla debe seguir funcionando (sin crash). No es un estado de negocio.
     */
    DESCONOCIDO("DESCONOCIDO");

    companion object {
        /** Estados de negocio que se muestran en filtros y leyenda. */
        val filtrables: List<EstadoMesa> = listOf(LIBRE, OCUPADA, ATENDIDA, CUENTA_SOLICITADA)

        fun desdeApi(valor: String?): EstadoMesa =
            values().firstOrNull { it.valorApi.equals(valor?.trim(), ignoreCase = true) } ?: DESCONOCIDO
    }
}
