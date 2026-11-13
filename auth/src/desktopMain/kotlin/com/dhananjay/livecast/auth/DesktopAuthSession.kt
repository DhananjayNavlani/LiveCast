package com.dhananjay.livecast.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.util.Properties

data class StoredSession(
    val user: User,
    val idToken: String,
    val refreshToken: String?
)

interface AuthSessionStore {
    fun load(): StoredSession?
    fun save(session: StoredSession)
    fun updateTokens(idToken: String, refreshToken: String?)
    fun clear()
}

class PropertiesAuthSessionStore(
    private val configDir: File
) : AuthSessionStore {

    private val authFile: File
        get() = File(configDir, "auth.properties")

    override fun load(): StoredSession? {
        return try {
            val file = authFile
            if (!file.exists()) return null
            val props = Properties().apply {
                file.inputStream().use { load(it) }
            }
            val idToken = props.getProperty("id_token") ?: return null
            val userId = props.getProperty("user_id") ?: ""
            val user = User(
                id = userId,
                email = props.getProperty("email"),
                displayName = props.getProperty("display_name"),
                isAnonymous = props.getProperty("is_anonymous")?.toBoolean() ?: false
            )
            val refreshToken = props.getProperty("refresh_token")
            StoredSession(user = user, idToken = idToken, refreshToken = refreshToken)
        } catch (_: Exception) {
            null
        }
    }

    override fun save(session: StoredSession) {
        try {
            if (!configDir.exists()) configDir.mkdirs()
            val props = Properties().apply {
                setProperty("user_id", session.user.id)
                session.user.email?.let { setProperty("email", it) }
                session.user.displayName?.let { setProperty("display_name", it) }
                setProperty("is_anonymous", session.user.isAnonymous.toString())
                setProperty("id_token", session.idToken)
                session.refreshToken?.let { setProperty("refresh_token", it) }
            }
            authFile.outputStream().use { props.store(it, "LiveCast Auth") }
        } catch (_: Exception) {
            // Silently ignore storage failures
        }
    }

    override fun updateTokens(idToken: String, refreshToken: String?) {
        try {
            if (!configDir.exists()) configDir.mkdirs()
            val file = authFile
            val props = Properties()
            if (file.exists()) {
                file.inputStream().use { props.load(it) }
            }
            props.setProperty("id_token", idToken)
            if (refreshToken != null) {
                props.setProperty("refresh_token", refreshToken)
            }
            file.outputStream().use { props.store(it, "LiveCast Auth") }
        } catch (_: Exception) {
            // Silently ignore storage failures
        }
    }

    override fun clear() {
        try {
            val file = authFile
            if (file.exists()) {
                file.delete()
            }
        } catch (_: Exception) {
            // Silently ignore deletion failures
        }
    }
}

interface TokenRefresher {
    suspend fun refresh(refreshToken: String): Pair<String, String?>?
}

class SecureTokenRefresher(
    private val apiKeyProvider: () -> String?
) : TokenRefresher {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun refresh(refreshToken: String): Pair<String, String?>? {
        val apiKey = apiKeyProvider()?.takeIf { it.isNotBlank() } ?: return null
        return withContext(Dispatchers.IO) {
            try {
                val url = URI("https://securetoken.googleapis.com/v1/token?key=$apiKey").toURL()
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                connection.doOutput = true

                val requestBody = "grant_type=refresh_token&refresh_token=" + URLEncoder.encode(refreshToken, "UTF-8")
                connection.outputStream.use { it.write(requestBody.toByteArray(Charsets.UTF_8)) }

                val responseCode = connection.responseCode
                if (responseCode == 200) {
                    val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
                    val tokenResponse = json.decodeFromString<SecureTokenResponse>(responseBody)
                    val newIdToken = tokenResponse.id_token ?: return@withContext null
                    Pair(newIdToken, tokenResponse.refresh_token)
                } else {
                    null
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    @Serializable
    private data class SecureTokenResponse(
        val access_token: String? = null,
        val expires_in: String? = null,
        val token_type: String? = null,
        val refresh_token: String? = null,
        val id_token: String? = null,
        val user_id: String? = null,
        val project_id: String? = null
    )
}
