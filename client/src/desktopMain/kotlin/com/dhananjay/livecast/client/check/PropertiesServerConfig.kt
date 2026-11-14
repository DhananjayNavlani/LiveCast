package com.dhananjay.livecast.client.check

import java.io.File
import java.util.Properties

class PropertiesServerConfig(
    private val configDir: File,
    private val env: Map<String, String>,
) : ServerConfig {

    override val baseUrl: String? by lazy {
        readBaseUrl()
    }

    private fun readBaseUrl(): String? {
        val envUrl = env["LIVECAST_SERVER_URL"]
        if (envUrl != null) {
            return envUrl.trim().ifBlank { null }
        }
        val file = File(configDir, "config.properties")
        if (!file.exists()) return null
        val props = Properties()
        try {
            file.inputStream().use { props.load(it) }
            return props.getProperty("livecast_server_url")?.trim()?.ifBlank { null }
        } catch (_: Exception) {
            return null
        }
    }
}
