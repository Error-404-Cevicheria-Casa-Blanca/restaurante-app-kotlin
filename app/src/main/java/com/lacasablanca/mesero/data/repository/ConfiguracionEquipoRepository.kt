package com.lacasablanca.mesero.data.repository

import com.lacasablanca.mesero.domain.equipo.ErrorTerminal
import com.lacasablanca.mesero.domain.equipo.ErrorUrl
import com.lacasablanca.mesero.domain.model.ConfiguracionEquipo
import kotlinx.coroutines.flow.Flow

/**
 * Contrato de la configuracion de equipo (PRB-99). Se expone como interfaz para
 * inyectar un fake en los tests del ViewModel y de la pantalla.
 */
interface ConfiguracionEquipoRepository {

    /** Lectura reactiva de la configuracion efectiva (guardada o fallback). */
    val configuracion: Flow<ConfiguracionEquipo>

    /** Valor sincrono para el interceptor HTTP y el login. */
    fun urlActual(): String

    /** Valor sincrono del terminal para el login y el PIN. */
    fun terminalActual(): String

    /** Lee la configuracion persistida aplicando el fallback de BuildConfig. */
    suspend fun configuracionActual(): ConfiguracionEquipo

    /** Valida y persiste. No llama al backend: el terminal se valida al iniciar sesion. */
    suspend fun guardar(serverUrl: String, terminalCodigo: String): ResultadoConfiguracion
}

sealed interface ResultadoConfiguracion {
    data class Guardado(val configuracion: ConfiguracionEquipo) : ResultadoConfiguracion

    data class Invalido(
        val errorUrl: ErrorUrl? = null,
        val errorTerminal: ErrorTerminal? = null
    ) : ResultadoConfiguracion
}
