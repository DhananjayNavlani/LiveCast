package com.dhananjay.livecast.client.pairing

import com.dhananjay.livecast.auth.authModule
import com.dhananjay.livecast.firestore.grpc.FirestoreConfigSource
import com.dhananjay.livecast.firestore.grpc.FirestoreConnectionConfig
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertIs

class DesktopPairingModuleTest {

    @Test
    fun resolvesUnsupportedRepositoriesWhenConfigAbsent() {
        val fakeConfigModule = module {
            single<FirestoreConfigSource> {
                object : FirestoreConfigSource {
                    override fun load(): FirestoreConnectionConfig? = null
                }
            }
        }

        val app = koinApplication {
            allowOverride(true)
            modules(authModule, clientPairingModule, fakeConfigModule)
        }

        val requestRepo = app.koin.get<PairingRequestRepository>()
        val deviceRepo = app.koin.get<SupervisedDeviceRepository>()

        assertIs<UnsupportedPairingRequestRepository>(requestRepo)
        assertIs<UnsupportedSupervisedDeviceRepository>(deviceRepo)

        app.close()
    }
}
