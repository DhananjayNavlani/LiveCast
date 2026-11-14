package com.dhananjay.livecast.client.check

import io.ktor.client.HttpClient
import org.koin.core.module.Module
import org.koin.dsl.module

expect val checkPlatformModule: Module

val clientCheckModule: Module = module {
    includes(checkPlatformModule)
    single<HttpClient> { platformHttpClient() }
    single<DeviceCheckService> { KtorDeviceCheckService(get(), get(), get()) }
}
