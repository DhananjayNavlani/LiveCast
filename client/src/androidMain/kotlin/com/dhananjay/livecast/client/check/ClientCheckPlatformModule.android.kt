package com.dhananjay.livecast.client.check

import org.koin.core.module.Module
import org.koin.dsl.module

actual val checkPlatformModule: Module = module {
    // ServerConfig is provided by androidClientApp via BuildConfigServerConfig
}
