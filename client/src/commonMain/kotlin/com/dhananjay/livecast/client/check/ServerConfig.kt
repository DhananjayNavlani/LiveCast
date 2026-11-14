package com.dhananjay.livecast.client.check

interface ServerConfig {
    val baseUrl: String?
}

class BuildConfigServerConfig(rawUrl: String?) : ServerConfig {
    override val baseUrl: String? = rawUrl?.trim()?.ifBlank { null }
}
