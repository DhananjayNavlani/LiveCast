package com.dhananjay.livecast.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URI

interface VerificationEmailSender {
    suspend fun send(idToken: String): Result<Unit>
}

class RestVerificationEmailSender(
    private val apiKeyProvider: () -> String?
) : VerificationEmailSender {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun send(idToken: String): Result<Unit> {
        val apiKey = apiKeyProvider()?.takeIf { it.isNotBlank() }
            ?: return Result.failure(IllegalStateException("Firebase API key not configured. Set FIREBASE_API_KEY environment variable or add firebase_api_key to ~/.livecast/config.properties"))

        return withContext(Dispatchers.IO) {
            try {
                val url = URI("https://identitytoolkit.googleapis.com/v1/accounts:sendOobCode?key=$apiKey").toURL()
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true

                @Serializable
                data class VerifyEmailRequest(val requestType: String, val idToken: String)

                val requestBody = json.encodeToString(
                    VerifyEmailRequest.serializer(),
                    VerifyEmailRequest("VERIFY_EMAIL", idToken)
                )

                connection.outputStream.use { it.write(requestBody.toByteArray()) }

                val responseCode = connection.responseCode
                if (responseCode == 200) {
                    Result.success(Unit)
                } else {
                    val responseBody = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                    val errorMessage = try {
                        val errorResponse = json.decodeFromString<FirebaseErrorResponse>(responseBody)
                        errorResponse.error.message
                    } catch (_: Exception) {
                        "Failed to send verification email (HTTP $responseCode)"
                    }
                    Result.failure(Exception(errorMessage))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
