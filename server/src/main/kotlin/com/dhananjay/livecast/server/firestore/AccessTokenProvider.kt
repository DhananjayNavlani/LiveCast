package com.dhananjay.livecast.server.firestore

import com.dhananjay.livecast.server.AccessTokenProvider
import com.google.auth.oauth2.GoogleCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.FileInputStream

class GoogleAuthAccessTokenProvider(
    private val credentials: GoogleCredentials
) : AccessTokenProvider {

    override suspend fun token(): String = withContext(Dispatchers.IO) {
        credentials.refreshIfExpired()
        credentials.accessToken.tokenValue
    }

    companion object {
        fun create(credentialsJson: String?, credentialsPath: String?): GoogleAuthAccessTokenProvider? {
            val scopes = listOf(
                "https://www.googleapis.com/auth/datastore",
                "https://www.googleapis.com/auth/firebase.messaging"
            )
            return try {
                val base = when {
                    !credentialsJson.isNullOrBlank() -> {
                        ByteArrayInputStream(credentialsJson.toByteArray(Charsets.UTF_8)).use {
                            GoogleCredentials.fromStream(it)
                        }
                    }
                    !credentialsPath.isNullOrBlank() -> {
                        FileInputStream(credentialsPath).use {
                            GoogleCredentials.fromStream(it)
                        }
                    }
                    else -> GoogleCredentials.getApplicationDefault()
                }
                GoogleAuthAccessTokenProvider(base.createScoped(scopes))
            } catch (_: Exception) {
                null
            }
        }
    }
}
