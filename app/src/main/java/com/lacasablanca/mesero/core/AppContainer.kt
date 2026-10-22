package com.lacasablanca.mesero.core

import android.content.Context
import com.lacasablanca.mesero.BuildConfig
import com.lacasablanca.mesero.data.local.CryptoManager
import com.lacasablanca.mesero.data.local.EncryptedTokenStore
import com.lacasablanca.mesero.data.local.SessionManager
import com.lacasablanca.mesero.data.local.TokenStore
import com.lacasablanca.mesero.data.remote.ApiClient
import com.lacasablanca.mesero.data.remote.AuthApi
import com.lacasablanca.mesero.data.remote.MesasApi
import com.lacasablanca.mesero.data.repository.AuthRepository
import com.lacasablanca.mesero.data.repository.FakeAuthRepository
import com.lacasablanca.mesero.data.repository.FakeMesaRepository
import com.lacasablanca.mesero.data.repository.MesaRepository
import com.lacasablanca.mesero.data.repository.RealAuthRepository
import com.lacasablanca.mesero.data.repository.RealMesaRepository
import retrofit2.Retrofit

/**
 * Inyeccion manual de dependencias (sin Hilt).
 * Con BuildConfig.USE_FAKE_DATA = true la app funciona sin backend (datos FAKE).
 */
class AppContainer(contexto: Context) {

    private val tokenStore: TokenStore = EncryptedTokenStore(contexto, CryptoManager())

    val sessionManager = SessionManager(tokenStore)

    private val retrofit: Retrofit by lazy {
        ApiClient.crear(BuildConfig.BASE_URL) { sessionManager.token }
    }

    private val authApi: AuthApi by lazy { retrofit.create(AuthApi::class.java) }
    private val mesasApi: MesasApi by lazy { retrofit.create(MesasApi::class.java) }

    val authRepository: AuthRepository =
        if (BuildConfig.USE_FAKE_DATA) {
            FakeAuthRepository(sessionManager)
        } else {
            RealAuthRepository(authApi, sessionManager)
        }

    val mesaRepository: MesaRepository =
        if (BuildConfig.USE_FAKE_DATA) {
            FakeMesaRepository()
        } else {
            RealMesaRepository(mesasApi)
        }
}
