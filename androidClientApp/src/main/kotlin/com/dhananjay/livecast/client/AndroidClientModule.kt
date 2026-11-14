package com.dhananjay.livecast.client

import com.dhananjay.livecast.client.check.BuildConfigServerConfig
import com.dhananjay.livecast.client.check.ServerConfig
import org.koin.dsl.module

val androidClientModule = module {
    single<ServerConfig> { BuildConfigServerConfig(BuildConfig.LIVECAST_SERVER_URL) }
}
