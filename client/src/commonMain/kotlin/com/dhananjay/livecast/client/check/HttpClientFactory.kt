package com.dhananjay.livecast.client.check

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.HttpTimeout

expect fun platformHttpEngine(): HttpClientEngineFactory<*>

fun platformHttpClient(
    engine: HttpClientEngineFactory<*> = platformHttpEngine()
): HttpClient = HttpClient(engine) {
    install(HttpTimeout) {
        requestTimeoutMillis = 10_000
        connectTimeoutMillis = 10_000
    }
}
