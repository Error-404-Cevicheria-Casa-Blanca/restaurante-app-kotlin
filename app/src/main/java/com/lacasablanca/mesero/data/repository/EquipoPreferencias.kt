package com.lacasablanca.mesero.data.repository

import com.lacasablanca.mesero.BuildConfig
import com.lacasablanca.mesero.data.local.CacheEquipo
import com.lacasablanca.mesero.data.local.EquipoAlmacen
import com.lacasablanca.mesero.data.local.EquipoGuardado
import com.lacasablanca.mesero.domain.equipo.ResultadoTerminal
import com.lacasablanca.mesero.domain.equipo.ResultadoUrl
import com.lacasablanca.mesero.domain.equipo.ValidadorEquipo
import com.lacasablanca.mesero.domain.model.ConfiguracionEquipo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Configuracion de equipo por tablet (PRB-99).
 *
 * DataStore es la fuente de verdad y [cache] es el espejo sincrono que usan el
 * interceptor HTTP y el repositorio de autenticacion. `BuildConfig.BASE_URL` y
 * `BuildConfig.TERMINAL_CODIGO` solo actuan como valores iniciales.
 */
class EquipoPreferencias(
    private val almacen: EquipoAlmacen,
    private val cache: CacheEquipo,
    private val urlPorDefecto: String = BuildConfig.BASE_URL,
    private val terminalPorDefecto: String = BuildConfig.TERMINAL_CODIGO,
    private val permitirHttp: Boolean = BuildConfig.DEBUG
) : ConfiguracionEquipoRepository {

    override val configuracion: Flow<ConfiguracionEquipo> =
        almacen.cambios.map { it.aEfectiva() }

    override fun urlActual(): String =
        cache.url()?.takeIf { it.isNotBlank() } ?: urlPorDefecto

    override fun terminalActual(): String =
        cache.terminal()?.takeIf { it.isNotBlank() } ?: terminalPorDefecto

    override suspend fun configuracionActual(): ConfiguracionEquipo =
        almacen.leer().aEfectiva()

    override suspend fun guardar(
        serverUrl: String,
        terminalCodigo: String
    ): ResultadoConfiguracion {
        val url = ValidadorEquipo.validarUrl(serverUrl, permitirHttp)
        val terminal = ValidadorEquipo.validarTerminal(terminalCodigo)
        if (url !is ResultadoUrl.Valida || terminal !is ResultadoTerminal.Valido) {
            return ResultadoConfiguracion.Invalido(
                errorUrl = (url as? ResultadoUrl.Invalida)?.error,
                errorTerminal = (terminal as? ResultadoTerminal.Invalido)?.error
            )
        }
        almacen.guardar(EquipoGuardado(url.url, terminal.codigo))
        cache.guardar(url.url, terminal.codigo)
        return ResultadoConfiguracion.Guardado(
            ConfiguracionEquipo(serverUrl = url.url, terminalCodigo = terminal.codigo)
        )
    }

    private fun EquipoGuardado.aEfectiva(): ConfiguracionEquipo = ConfiguracionEquipo(
        serverUrl = serverUrl?.takeIf { it.isNotBlank() } ?: urlPorDefecto,
        terminalCodigo = terminalCodigo?.takeIf { it.isNotBlank() } ?: terminalPorDefecto
    )
}
