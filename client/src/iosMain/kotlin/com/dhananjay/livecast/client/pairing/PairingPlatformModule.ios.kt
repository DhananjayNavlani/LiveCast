package com.dhananjay.livecast.client.pairing

import org.koin.core.module.Module
import org.koin.dsl.module

actual val pairingPlatformModule: Module = module {
    single<PairingRequestRepository> { UnsupportedPairingRequestRepository() }
    single<SupervisedDeviceRepository> { UnsupportedSupervisedDeviceRepository() }
}
