package com.dhananjay.livecast.server.firestore

import com.dhananjay.livecast.protocol.GroupFields
import com.dhananjay.livecast.protocol.GroupRole
import com.dhananjay.livecast.protocol.GroupRules
import com.dhananjay.livecast.server.AccessTokenProvider
import com.dhananjay.livecast.server.DeviceDirectory
import com.dhananjay.livecast.server.MembershipDirectory
import com.dhananjay.livecast.server.UpstreamException
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLPathPart
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.coroutines.cancellation.CancellationException

class FirestoreRestDirectory(
    private val httpClient: HttpClient,
    private val projectId: String,
    private val tokenProvider: AccessTokenProvider?,
    private val emulatorHost: String?
) : DeviceDirectory, MembershipDirectory {

    private val json = Json { ignoreUnknownKeys = true }
    private val baseUrl = if (!emulatorHost.isNullOrBlank()) {
        "http://$emulatorHost"
    } else {
        "https://firestore.googleapis.com"
    }

    private suspend fun authToken(): String {
        return if (!emulatorHost.isNullOrBlank()) {
            "owner"
        } else {
            tokenProvider?.token() ?: throw UpstreamException("No AccessTokenProvider configured for Firestore")
        }
    }

    override suspend fun groupOf(deviceId: String): String? {
        val encodedDeviceId = deviceId.encodeURLPathPart()
        val url = "$baseUrl/v1/projects/$projectId/databases/(default)/documents/devices/$encodedDeviceId"
        val root = getDocument(url) ?: return null
        val fields = root["fields"]?.jsonObject ?: return null
        val groupField = fields[GroupFields.GROUP_ID]?.jsonObject ?: return null
        if (groupField.containsKey("nullValue")) return null
        val groupStr = groupField["stringValue"]?.jsonPrimitive?.content
        return if (groupStr.isNullOrEmpty()) null else groupStr
    }

    override suspend fun pushTokenOf(deviceId: String): String? {
        val encodedDeviceId = deviceId.encodeURLPathPart()
        val url = "$baseUrl/v1/projects/$projectId/databases/(default)/documents/devices/$encodedDeviceId/private/push"
        val root = getDocument(url) ?: return null
        val fields = root["fields"]?.jsonObject ?: return null
        val tokenField = fields[GroupFields.FCM_TOKEN]?.jsonObject ?: return null
        val tokenStr = tokenField["stringValue"]?.jsonPrimitive?.content
        return if (tokenStr.isNullOrEmpty()) null else tokenStr
    }

    override suspend fun roleOf(groupId: String, email: String): GroupRole? {
        val encodedGroupId = groupId.encodeURLPathPart()
        val encodedEmail = GroupRules.normalizeEmail(email).encodeURLPathPart()
        val url = "$baseUrl/v1/projects/$projectId/databases/(default)/documents/groups/$encodedGroupId/members/$encodedEmail"
        val root = getDocument(url) ?: return null
        val fields = root["fields"]?.jsonObject ?: return null
        val roleField = fields[GroupFields.ROLE]?.jsonObject ?: return null
        val roleStr = roleField["stringValue"]?.jsonPrimitive?.content
        return GroupRules.roleFrom(roleStr)
    }

    private suspend fun getDocument(url: String): kotlinx.serialization.json.JsonObject? {
        return try {
            val response = httpClient.get(url) {
                header(HttpHeaders.Authorization, "Bearer ${authToken()}")
            }
            if (response.status == HttpStatusCode.NotFound) {
                return null
            }
            if (!response.status.isSuccess()) {
                throw UpstreamException("Firestore returned HTTP ${response.status.value}")
            }
            json.parseToJsonElement(response.bodyAsText()).jsonObject
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (e is UpstreamException) throw e
            throw UpstreamException("Firestore request failed", e)
        }
    }
}
