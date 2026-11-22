package com.lacasablanca.mesero.data.remote

import com.lacasablanca.mesero.BuildConfig
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Construye el cliente HTTP.
 * El token viaja en el header Authorization y NUNCA se registra en los logs
 * (el interceptor de log esta redactado y solo actua en debug).
 */
object ApiClient {

    fun crear(
        baseUrl: String,
        proveedorToken: () -> String?,
        proveedorBaseUrl: () -> String? = { null }
    ): Retrofit {
        val registro = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
            redactHeader("Authorization")
        }

        val cliente = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .addInterceptor { cadena ->
                // PRB-99: la URL configurada se aplica en cada peticion sin
                // recrear Retrofit. `baseUrl` queda como base fija de las rutas.
                val baseConfigurada = proveedorBaseUrl()?.takeIf { it.isNotBlank() } ?: baseUrl
                var peticion = conBaseConfigurada(cadena.request(), baseConfigurada)
                // PRB-95: el tunel de pruebas (loca.lt) exige este header.
                if (BuildConfig.BYPASS_TUNNEL && baseConfigurada.contains("loca.lt", ignoreCase = true)) {
                    peticion = peticion.newBuilder()
                        .header("Bypass-Tunnel-Reminder", "true")
                        .build()
                }
                val token = proveedorToken()
                if (!token.isNullOrBlank()) {
                    peticion = peticion.newBuilder()
                        .header("Authorization", "Bearer $token")
                        .build()
                }
                cadena.proceed(peticion)
            }
            .addInterceptor(registro)
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(cliente)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    /**
     * Reescribe esquema, host, puerto y prefijo de ruta con la URL configurada.
     * Devuelve `null` si la configuracion no es una URL valida.
     */
    internal fun reescribirUrl(original: HttpUrl, baseConfigurada: String): HttpUrl? {
        val base = baseConfigurada.toHttpUrlOrNull() ?: return null
        val prefijo = base.encodedPath.trimEnd('/')
        val ruta = if (prefijo.isEmpty()) original.encodedPath else prefijo + original.encodedPath
        return original.newBuilder()
            .scheme(base.scheme)
            .host(base.host)
            .port(base.port)
            .encodedPath(ruta)
            .build()
    }

    private fun conBaseConfigurada(peticion: Request, baseConfigurada: String): Request {
        val url = reescribirUrl(peticion.url, baseConfigurada) ?: return peticion
        return peticion.newBuilder().url(url).build()
    }
}
