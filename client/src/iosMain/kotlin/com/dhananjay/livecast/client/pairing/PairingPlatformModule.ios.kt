package com.dhananjay.livecast.client.pairing

import com.dhananjay.livecast.client.firestorePlatformModule
import org.koin.core.module.Module
import org.koin.dsl.module

actual val pairingPlatformModule: Module = module {
    includes(firestorePlatformModule)
    single<PairingRequestRepository> { UnsupportedPairingRequestRepository() }
    single<SupervisedDeviceRepository> { UnsupportedSupervisedDeviceRepository() }
}

