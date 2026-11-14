package com.dhananjay.livecast.client.check

import com.dhananjay.livecast.auth.IdTokenProvider
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class CheckIdResponse(val checkId: String)

class KtorDeviceCheckService(
    private val http: HttpClient,
    private val config: ServerConfig,
    private val tokens: IdTokenProvider,
) : DeviceCheckService {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun requestCheck(deviceId: String): CheckRequestResult {
        val baseUrl = config.baseUrl
        if (baseUrl.isNullOrBlank()) {
            return CheckRequestResult.Failed("Server not configured")
        }

        try {
            val initialToken = tokens.getIdToken(forceRefresh = false)
                ?: return CheckRequestResult.NotAllowed

            val url = "${baseUrl.trimEnd('/')}/v1/devices/$deviceId/checks"
            var response = executeCheck(url, initialToken)

            if (response.status == HttpStatusCode.Unauthorized) {
                val freshToken = tokens.getIdToken(forceRefresh = true)
                    ?: return CheckRequestResult.NotAllowed
                response = executeCheck(url, freshToken)
                if (response.status == HttpStatusCode.Unauthorized) {
                    return CheckRequestResult.NotAllowed
                }
            }

            return mapResponse(response)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return CheckRequestResult.Failed(e.message ?: "Unknown error")
        }
    }

    private suspend fun executeCheck(url: String, token: String): HttpResponse {
        return http.post(url) {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
    }

    private suspend fun mapResponse(response: HttpResponse): CheckRequestResult {
        return when (response.status) {
            HttpStatusCode.Accepted -> {
                val bodyText = response.bodyAsText()
                try {
                    val checkId = json.decodeFromString<CheckIdResponse>(bodyText).checkId
                    CheckRequestResult.Requested(checkId)
                } catch (_: Exception) {
                    CheckRequestResult.Failed("Malformed response: $bodyText")
                }
            }
            HttpStatusCode.Unauthorized -> CheckRequestResult.NotAllowed
            HttpStatusCode.Forbidden -> CheckRequestResult.NotAllowed
            HttpStatusCode.NotFound -> CheckRequestResult.NotAllowed
            HttpStatusCode.Conflict -> CheckRequestResult.NoPushToken
            HttpStatusCode.Gone -> CheckRequestResult.AppRemoved
            HttpStatusCode.TooManyRequests -> CheckRequestResult.RateLimited
            else -> {
                val bodyText = response.bodyAsText()
                CheckRequestResult.Failed(
                    if (bodyText.isNotBlank()) bodyText else "HTTP ${response.status.value}"
                )
            }
        }
    }
}
