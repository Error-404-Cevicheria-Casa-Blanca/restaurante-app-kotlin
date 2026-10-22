package com.lacasablanca.mesero.domain

/** Resultado de una operacion de datos, con errores tipados para la UI. */
sealed interface Resultado<out T> {
    data class Exito<out T>(val datos: T) : Resultado<T>
    data class Fallo(val error: ErrorApp) : Resultado<Nothing>
}

/** Errores que la UI sabe traducir a un mensaje en espanol. */
enum class ErrorApp {
    CREDENCIALES,
    RED,
    SESION_EXPIRADA,
    ROL_NO_PERMITIDO,
    CONFLICTO,
    NO_ENCONTRADO,
    SERVIDOR,
    DESCONOCIDO
}

/** Traduce un codigo HTTP a un error de dominio (401 y 403 = sesion expirada, D9). */
fun errorDesdeCodigoHttp(codigo: Int): ErrorApp = when (codigo) {
    401, 403 -> ErrorApp.SESION_EXPIRADA
    404 -> ErrorApp.NO_ENCONTRADO
    409 -> ErrorApp.CONFLICTO
    in 500..599 -> ErrorApp.SERVIDOR
    else -> ErrorApp.DESCONOCIDO
}
