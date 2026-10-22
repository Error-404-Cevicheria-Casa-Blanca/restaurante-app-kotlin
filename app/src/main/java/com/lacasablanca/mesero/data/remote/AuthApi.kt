package com.lacasablanca.mesero.data.remote

import com.lacasablanca.mesero.data.remote.dto.LoginRequestDto
import com.lacasablanca.mesero.data.remote.dto.LoginResponseDto
import retrofit2.http.Body
import retrofit2.http.POST

/** Contrato v1: POST /api/v1/auth/login. */
interface AuthApi {
    @POST("api/v1/auth/login")
    suspend fun login(@Body request: LoginRequestDto): LoginResponseDto
}
