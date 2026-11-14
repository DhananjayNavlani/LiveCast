package com.dhananjay.livecast.client.check

import org.koin.core.module.Module
import org.koin.dsl.module
import java.io.File

actual val checkPlatformModule: Module = module {
    single<ServerConfig> {
        PropertiesServerConfig(
            configDir = File(System.getProperty("user.home"), ".livecast"),
            env = System.getenv(),
        )
    }
}
