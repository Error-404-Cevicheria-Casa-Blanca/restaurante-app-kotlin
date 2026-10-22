package com.lacasablanca.mesero.data.remote

import com.lacasablanca.mesero.BuildConfig
import okhttp3.OkHttpClient
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

    fun crear(baseUrl: String, proveedorToken: () -> String?): Retrofit {
        val registro = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
            redactHeader("Authorization")
        }

        val cliente = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .addInterceptor { cadena ->
                val token = proveedorToken()
                val peticion = if (token.isNullOrBlank()) {
                    cadena.request()
                } else {
                    cadena.request().newBuilder()
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
}
