package com.dhananjay.livecast.server.push

import com.dhananjay.livecast.protocol.CheckRules
import com.dhananjay.livecast.server.AccessTokenProvider
import com.dhananjay.livecast.server.PushResult
import com.dhananjay.livecast.server.PushSender
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

class FcmHttpV1PushSender(
    private val httpClient: HttpClient,
    private val projectId: String,
    private val tokenProvider: AccessTokenProvider?,
    private val fcmUrl: String = "https://fcm.googleapis.com/v1/projects/$projectId/messages:send"
) : PushSender {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun sendCheck(token: String, checkId: String): PushResult {
        val accessToken = tokenProvider?.let {
            try {
                it.token()
            } catch (e: Exception) {
                return PushResult.Failed("Failed to obtain access token: ${e.message}")
            }
        } ?: return PushResult.Failed("No Google credentials configured")

        val payload = buildJsonObject {
            putJsonObject("message") {
                put("token", token)
                putJsonObject("data") {
                    put(CheckRules.DATA_TYPE, CheckRules.TYPE_CHECK)
                    put(CheckRules.DATA_CHECK_ID, checkId)
                }
                putJsonObject("android") {
                    put("priority", "HIGH")
                    put("ttl", "${CheckRules.FCM_TTL_SECONDS}s")
                }
            }
        }.toString()

        return try {
            val response = httpClient.post(fcmUrl) {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
                contentType(ContentType.Application.Json)
                setBody(payload)
            }

            if (response.status == HttpStatusCode.NotFound) {
                return PushResult.Unregistered
            }

            if (response.status.isSuccess()) {
                return PushResult.Sent
            }

            val bodyText = response.bodyAsText()
            if (isUnregisteredError(bodyText)) {
                PushResult.Unregistered
            } else {
                PushResult.Failed("FCM send failed: ${response.status.value} $bodyText")
            }
        } catch (e: Exception) {
            PushResult.Failed(e.message ?: "FCM request failed")
        }
    }

    private fun isUnregisteredError(bodyText: String): Boolean {
        if (bodyText.contains("UNREGISTERED")) return true
        return try {
            val root = json.parseToJsonElement(bodyText).jsonObject
            val error = root["error"]?.jsonObject ?: return false
            val details = error["details"]?.jsonArray ?: return false
            details.any { detail ->
                detail.jsonObject["errorCode"]?.jsonPrimitive?.content == "UNREGISTERED"
            }
        } catch (_: Exception) {
            false
        }
    }
}
