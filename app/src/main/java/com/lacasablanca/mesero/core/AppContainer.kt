package com.lacasablanca.mesero.core

import android.content.Context
import com.lacasablanca.mesero.BuildConfig
import com.lacasablanca.mesero.data.local.CryptoManager
import com.lacasablanca.mesero.data.local.EncryptedTokenStore
import com.lacasablanca.mesero.data.local.SessionManager
import com.lacasablanca.mesero.data.local.TokenStore
import com.lacasablanca.mesero.data.remote.ApiClient
import com.lacasablanca.mesero.data.remote.AuthApi
import com.lacasablanca.mesero.data.remote.CatalogoApi
import com.lacasablanca.mesero.data.remote.CobroApi
import com.lacasablanca.mesero.data.remote.MesasApi
import com.lacasablanca.mesero.data.remote.PedidosApi
import com.lacasablanca.mesero.data.repository.AuthRepository
import com.lacasablanca.mesero.data.repository.CatalogoRepository
import com.lacasablanca.mesero.data.repository.CobroRepository
import com.lacasablanca.mesero.data.repository.FakeAuthRepository
import com.lacasablanca.mesero.data.repository.FakeCatalogoRepository
import com.lacasablanca.mesero.data.repository.FakeCobroRepository
import com.lacasablanca.mesero.data.repository.FakeMesaRepository
import com.lacasablanca.mesero.data.repository.FakePedidoRepository
import com.lacasablanca.mesero.data.repository.MesaRepository
import com.lacasablanca.mesero.data.repository.PedidoRepository
import com.lacasablanca.mesero.data.repository.RealAuthRepository
import com.lacasablanca.mesero.data.repository.RealCatalogoRepository
import com.lacasablanca.mesero.data.repository.RealCobroRepository
import com.lacasablanca.mesero.data.repository.RealMesaRepository
import com.lacasablanca.mesero.data.repository.RealPedidoRepository
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
    private val catalogoApi: CatalogoApi by lazy { retrofit.create(CatalogoApi::class.java) }
    private val pedidosApi: PedidosApi by lazy { retrofit.create(PedidosApi::class.java) }
    private val cobroApi: CobroApi by lazy { retrofit.create(CobroApi::class.java) }

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

    val catalogoRepository: CatalogoRepository =
        if (BuildConfig.USE_FAKE_DATA) {
            FakeCatalogoRepository()
        } else {
            RealCatalogoRepository(catalogoApi)
        }

    val pedidoRepository: PedidoRepository =
        if (BuildConfig.USE_FAKE_DATA) {
            FakePedidoRepository()
        } else {
            RealPedidoRepository(pedidosApi)
        }

    val cobroRepository: CobroRepository =
        if (BuildConfig.USE_FAKE_DATA) {
            FakeCobroRepository(mesaRepository)
        } else {
            RealCobroRepository(cobroApi)
        }
}
