package com.lacasablanca.mesero.data.remote

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Reescritura de la URL base en cada peticion sin recrear Retrofit (PRB-99). */
class ApiClientUrlTest {

    private val original = "http://10.0.2.2:8080/api/v1/auth/login".toHttpUrl()

    @Test
    fun `la url configurada reemplaza esquema host y puerto`() {
        val nueva = ApiClient.reescribirUrl(original, "https://casa-blanca.local/")

        assertEquals("https://casa-blanca.local/api/v1/auth/login", nueva.toString())
    }

    @Test
    fun `la url configurada conserva su prefijo de ruta`() {
        val nueva = ApiClient.reescribirUrl(original, "https://casa-blanca.local/servidor/")

        assertEquals(
            "https://casa-blanca.local/servidor/api/v1/auth/login",
            nueva.toString()
        )
    }

    @Test
    fun `la url configurada conserva el puerto explicito`() {
        val nueva = ApiClient.reescribirUrl(original, "http://192.168.1.10:9090/")

        assertEquals("http://192.168.1.10:9090/api/v1/auth/login", nueva.toString())
    }

    @Test
    fun `una base invalida no reescribe la peticion`() {
        assertNull(ApiClient.reescribirUrl(original, "no-es-una-url"))
    }
}
