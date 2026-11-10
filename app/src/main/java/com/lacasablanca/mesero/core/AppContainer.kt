package com.lacasablanca.mesero.core

import android.content.Context
import com.lacasablanca.mesero.BuildConfig
import com.lacasablanca.mesero.data.local.CryptoManager
import com.lacasablanca.mesero.data.local.EncryptedTokenStore
import com.lacasablanca.mesero.data.local.SessionManager
import com.lacasablanca.mesero.data.local.TokenStore
import com.lacasablanca.mesero.data.local.offline.AppDatabase
import com.lacasablanca.mesero.data.local.offline.OutboxPedidoStore
import com.lacasablanca.mesero.data.local.offline.PedidoSync
import com.lacasablanca.mesero.data.local.offline.RoomOutboxPedidoStore
import com.lacasablanca.mesero.data.remote.ApiClient
import com.lacasablanca.mesero.data.remote.AuthApi
import com.lacasablanca.mesero.data.remote.CatalogoApi
import com.lacasablanca.mesero.data.remote.CobroApi
import com.lacasablanca.mesero.data.remote.MesasApi
import com.lacasablanca.mesero.data.remote.PedidosApi
import com.lacasablanca.mesero.data.repository.AuthRepository
import com.lacasablanca.mesero.data.repository.CatalogoOfflineRepository
import com.lacasablanca.mesero.data.repository.CatalogoRepository
import com.lacasablanca.mesero.data.repository.CobroRepository
import com.lacasablanca.mesero.data.repository.FakeAuthRepository
import com.lacasablanca.mesero.data.repository.FakeCatalogoRepository
import com.lacasablanca.mesero.data.repository.FakeCobroRepository
import com.lacasablanca.mesero.data.repository.FakeConsumoStore
import com.lacasablanca.mesero.data.repository.FakeMesaRepository
import com.lacasablanca.mesero.data.repository.FakePedidoRepository
import com.lacasablanca.mesero.data.repository.MesaOfflineRepository
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
class AppContainer(private val contexto: Context) {

    private val tokenStore: TokenStore = EncryptedTokenStore(contexto, CryptoManager())

    val sessionManager = SessionManager(tokenStore)

    /** Room: cache del catalogo/mesas + outbox de pedidos (PRB-62/63). */
    private val database: AppDatabase by lazy { AppDatabase.crear(contexto) }

    val outbox: OutboxPedidoStore by lazy {
        RoomOutboxPedidoStore(database.pedidoPendienteDao())
    }

    val networkMonitor: NetworkMonitor = NetworkMonitor(contexto).also { it.iniciar() }

    /** Consumo simulado compartido por los repositorios fake de pedido y cobro. */
    private val fakeConsumoStore = FakeConsumoStore()

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
            MesaOfflineRepository(RealMesaRepository(mesasApi), database.mesaDao())
        }

    val catalogoRepository: CatalogoRepository =
        if (BuildConfig.USE_FAKE_DATA) {
            FakeCatalogoRepository()
        } else {
            CatalogoOfflineRepository(RealCatalogoRepository(catalogoApi), database.catalogoDao())
        }

    /**
     * Instancia unica del fake de pedidos: la comparten la lista de Pedidos y
     * el cobro fake, para que un pedido cobrado desaparezca al refrescar.
     */
    private val fakePedidoRepository: FakePedidoRepository? =
        if (BuildConfig.USE_FAKE_DATA) FakePedidoRepository(fakeConsumoStore) else null

    val pedidoRepository: PedidoRepository =
        fakePedidoRepository ?: RealPedidoRepository(pedidosApi, outbox)

    /** Logica del SyncWorker (PRB-63); WorkManager la ejecuta en segundo plano. */
    val pedidoSync: PedidoSync by lazy { PedidoSync(outbox, pedidosApi) }

    val cobroRepository: CobroRepository =
        if (BuildConfig.USE_FAKE_DATA) {
            FakeCobroRepository(
                mesaRepository = mesaRepository,
                consumoStore = fakeConsumoStore,
                pedidoRepository = fakePedidoRepository
            )
        } else {
            RealCobroRepository(cobroApi)
        }
}
