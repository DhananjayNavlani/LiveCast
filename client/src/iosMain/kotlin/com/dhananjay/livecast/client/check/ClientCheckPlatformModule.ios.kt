package com.dhananjay.livecast.client.check

import org.koin.core.module.Module
import org.koin.dsl.module

actual val checkPlatformModule: Module = module {
    single<ServerConfig> {
        object : ServerConfig {
            override val baseUrl: String? = null
        }
    }
}
