package com.dhananjay.livecast.client.check

import com.dhananjay.livecast.auth.IdTokenProvider
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class CheckIdResponse(val checkId: String)

fun isAllowedServerUrl(rawUrl: String?): Boolean {
    if (rawUrl.isNullOrBlank()) return false
    val url = try {
        Url(rawUrl.trim())
    } catch (_: Exception) {
        return false
    }
    if (url.protocol == URLProtocol.HTTPS) return true
    if (url.protocol == URLProtocol.HTTP) {
        val host = url.host.lowercase()
        return host == "localhost" || host == "127.0.0.1" || host == "10.0.2.2"
    }
    return false
}

class KtorDeviceCheckService(
    private val http: HttpClient,
    private val config: ServerConfig,
    private val tokens: IdTokenProvider,
) : DeviceCheckService {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun requestCheck(deviceId: String): CheckRequestResult {
        val baseUrl = config.baseUrl
        if (!isAllowedServerUrl(baseUrl)) {
            return CheckRequestResult.Failed("Server not configured")
        }

        try {
            val initialToken = tokens.getIdToken(forceRefresh = false)
                ?: return CheckRequestResult.NotAllowed

            val url = "${baseUrl!!.trim().trimEnd('/')}/v1/devices/$deviceId/checks"
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
            currentCoroutineContext().ensureActive()
            return CheckRequestResult.Failed("Check failed")
        } catch (_: Exception) {
            return CheckRequestResult.Failed("Check failed")
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
                    CheckRequestResult.Failed("Check failed")
                }
            }
            HttpStatusCode.Unauthorized -> CheckRequestResult.NotAllowed
            HttpStatusCode.Forbidden -> CheckRequestResult.NotAllowed
            HttpStatusCode.NotFound -> CheckRequestResult.NotAllowed
            HttpStatusCode.Conflict -> CheckRequestResult.NoPushToken
            HttpStatusCode.Gone -> CheckRequestResult.AppRemoved
            HttpStatusCode.TooManyRequests -> CheckRequestResult.RateLimited
            else -> CheckRequestResult.Failed("Check failed (HTTP ${response.status.value})")
        }
    }
}
