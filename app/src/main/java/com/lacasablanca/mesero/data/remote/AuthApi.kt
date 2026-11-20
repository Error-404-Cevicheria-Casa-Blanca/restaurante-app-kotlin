package com.lacasablanca.mesero.data.remote

import com.lacasablanca.mesero.data.remote.dto.LoginRequestDto
import com.lacasablanca.mesero.data.remote.dto.LoginResponseDto
import com.lacasablanca.mesero.data.remote.dto.PerfilResponseDto
import com.lacasablanca.mesero.data.remote.dto.PinRequestDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/** Contrato v1: POST /api/v1/auth/login, POST /api/v1/auth/pin (PRB-54) y GET /api/v1/auth/me. */
interface AuthApi {
    @POST("api/v1/auth/login")
    suspend fun login(@Body request: LoginRequestDto): LoginResponseDto

    @POST("api/v1/auth/pin")
    suspend fun validarPin(@Body request: PinRequestDto): LoginResponseDto

    @GET("api/v1/auth/me")
    suspend fun perfil(): PerfilResponseDto
}
