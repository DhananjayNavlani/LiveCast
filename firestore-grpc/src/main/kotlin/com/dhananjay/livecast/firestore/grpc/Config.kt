package com.dhananjay.livecast.firestore.grpc

import java.io.File
import java.util.Properties

data class FirestoreConnectionConfig(
    val projectId: String,
    val host: String = "firestore.googleapis.com",
    val port: Int = 443,
    val plaintext: Boolean = false
)

interface FirestoreConfigSource {
    fun load(): FirestoreConnectionConfig?
}

class EnvPropertiesFirestoreConfigSource(
    private val configDir: File,
    private val env: (String) -> String? = System::getenv
) : FirestoreConfigSource {

    override fun load(): FirestoreConnectionConfig? {
        val projectId = env("FIREBASE_PROJECT_ID")?.takeIf { it.isNotBlank() }
            ?: loadProjectIdFromConfigFile()
            ?: return null

        val emulatorHost = env("FIRESTORE_EMULATOR_HOST")?.takeIf { it.isNotBlank() }
        val (host, port, plaintext) = if (emulatorHost != null) {
            val parts = emulatorHost.split(":")
            val h = parts[0].ifBlank { "localhost" }
            val p = parts.getOrNull(1)?.toIntOrNull() ?: 8080
            Triple(h, p, true)
        } else {
            Triple("firestore.googleapis.com", 443, false)
        }

        return FirestoreConnectionConfig(
            projectId = projectId,
            host = host,
            port = port,
            plaintext = plaintext
        )
    }

    private fun loadProjectIdFromConfigFile(): String? {
        val configFile = File(configDir, "config.properties")
        return try {
            if (configFile.exists()) {
                val props = Properties().apply {
                    configFile.inputStream().use { load(it) }
                }
                props.getProperty("firebase_project_id")?.takeIf { it.isNotBlank() }
            } else null
        } catch (_: Exception) {
            null
        }
    }
}
