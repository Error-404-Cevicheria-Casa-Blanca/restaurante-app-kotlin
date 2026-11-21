package com.lacasablanca.mesero.domain.equipo

import java.net.URI
import java.net.URISyntaxException

enum class ErrorUrl {
    VACIA,
    SIN_ESQUEMA,
    ESQUEMA_NO_PERMITIDO,
    SIN_HOST,
    CREDENCIALES,
    CONSULTA,
    FORMATO
}

enum class ErrorTerminal {
    VACIO,
    FORMATO
}

sealed interface ResultadoUrl {
    data class Valida(val url: String) : ResultadoUrl
    data class Invalida(val error: ErrorUrl) : ResultadoUrl
}

sealed interface ResultadoTerminal {
    data class Valido(val codigo: String) : ResultadoTerminal
    data class Invalido(val error: ErrorTerminal) : ResultadoTerminal
}

/**
 * Validacion local de la configuracion de equipo (PRB-99).
 *
 * La URL se normaliza con barra final y admite `http` solo cuando [permitirHttp]
 * es true (variante debug). El terminal se valida con un formato generico porque
 * el backend lo comprueba de verdad en `/auth/login` y `/auth/pin`.
 */
object ValidadorEquipo {

    private val FORMATO_TERMINAL = Regex("^[A-Za-z0-9][A-Za-z0-9_-]{1,29}$")

    fun validarUrl(valor: String, permitirHttp: Boolean): ResultadoUrl {
        val texto = valor.trim()
        val uri = try {
            URI(texto)
        } catch (e: URISyntaxException) {
            return ResultadoUrl.Invalida(ErrorUrl.FORMATO)
        }
        val error = errorDeUrl(texto, uri, permitirHttp)
        return if (error == null) {
            ResultadoUrl.Valida(normalizar(uri))
        } else {
            ResultadoUrl.Invalida(error)
        }
    }

    fun validarTerminal(valor: String): ResultadoTerminal {
        val codigo = valor.trim()
        val error = when {
            codigo.isEmpty() -> ErrorTerminal.VACIO
            !FORMATO_TERMINAL.matches(codigo) -> ErrorTerminal.FORMATO
            else -> null
        }
        return if (error == null) {
            ResultadoTerminal.Valido(codigo)
        } else {
            ResultadoTerminal.Invalido(error)
        }
    }

    private fun errorDeUrl(texto: String, uri: URI, permitirHttp: Boolean): ErrorUrl? = when {
        texto.isEmpty() -> ErrorUrl.VACIA
        uri.scheme.isNullOrBlank() -> ErrorUrl.SIN_ESQUEMA
        !esquemaPermitido(uri.scheme, permitirHttp) -> ErrorUrl.ESQUEMA_NO_PERMITIDO
        uri.userInfo != null -> ErrorUrl.CREDENCIALES
        uri.rawQuery != null -> ErrorUrl.CONSULTA
        uri.rawFragment != null -> ErrorUrl.FORMATO
        uri.host.isNullOrBlank() -> ErrorUrl.SIN_HOST
        else -> null
    }

    private fun esquemaPermitido(esquema: String, permitirHttp: Boolean): Boolean {
        val normalizado = esquema.lowercase()
        return normalizado == ESQUEMA_HTTPS || (permitirHttp && normalizado == ESQUEMA_HTTP)
    }

    private fun normalizar(uri: URI): String {
        val esquema = uri.scheme.lowercase()
        val autoridad = if (uri.port == -1) uri.host else "${uri.host}:${uri.port}"
        val ruta = uri.rawPath.orEmpty()
        val rutaFinal = when {
            ruta.isEmpty() -> "/"
            ruta.endsWith("/") -> ruta
            else -> "$ruta/"
        }
        return "$esquema://$autoridad$rutaFinal"
    }

    private const val ESQUEMA_HTTPS = "https"
    private const val ESQUEMA_HTTP = "http"
}
