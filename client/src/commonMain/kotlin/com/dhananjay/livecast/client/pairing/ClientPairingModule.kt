package com.dhananjay.livecast.client.pairing

import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

expect val pairingPlatformModule: Module

val clientPairingModule: Module = module {
    includes(pairingPlatformModule)
    viewModel { AddDeviceViewModel(get(), get()) }
    viewModelOf(::MyDevicesViewModel)
}
