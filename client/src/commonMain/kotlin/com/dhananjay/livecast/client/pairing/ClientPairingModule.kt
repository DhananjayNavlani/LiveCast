package com.dhananjay.livecast.client.pairing

import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val clientPairingModule: Module = module {
    single<ClientPairingRepository> { createClientPairingRepository(get()) }
    viewModel { ClientPairingViewModel(get(), get()) }
}
