package com.dhananjay.livecast.server

data class ServerConfig(
    val projectId: String,
    val credentialsJson: String?,
    val credentialsPath: String?,
    val port: Int,
    val corsOrigins: List<String>,
    val firestoreEmulatorHost: String?
) {
    companion object {
        fun fromEnv(env: Map<String, String>): ServerConfig {
            val projectId = env["FIREBASE_PROJECT_ID"]?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("FIREBASE_PROJECT_ID environment variable is required")

            val port = env["PORT"]?.toIntOrNull() ?: 8080
            val credentialsJson = env["GOOGLE_CREDENTIALS_JSON"]?.takeIf { it.isNotBlank() }
            val credentialsPath = env["GOOGLE_APPLICATION_CREDENTIALS"]?.takeIf { it.isNotBlank() }
            val firestoreEmulatorHost = env["FIRESTORE_EMULATOR_HOST"]?.takeIf { it.isNotBlank() }
            val corsOrigins = env["CORS_ALLOWED_ORIGINS"]
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?: emptyList()

            return ServerConfig(
                projectId = projectId,
                credentialsJson = credentialsJson,
                credentialsPath = credentialsPath,
                port = port,
                corsOrigins = corsOrigins,
                firestoreEmulatorHost = firestoreEmulatorHost
            )
        }
    }
}
