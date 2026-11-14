package com.dhananjay.livecast.server.push

import com.dhananjay.livecast.server.AccessTokenProvider
import com.dhananjay.livecast.server.PushResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FcmHttpV1PushSenderTest {

    private class FakeAccessTokenProvider(val tokenVal: String = "test-access-token") : AccessTokenProvider {
        override suspend fun token(): String = tokenVal
    }

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun sendsHighPriorityDataWithTtl() = runTest {
        var recordedAuthHeader: String? = null
        var recordedBody: String? = null

        val engine = MockEngine { request ->
            assertEquals("https://fcm.googleapis.com/v1/projects/my-proj/messages:send", request.url.toString())
            recordedAuthHeader = request.headers[HttpHeaders.Authorization]
            recordedBody = request.body.toByteArray().decodeToString()

            respond(
                content = """{"name":"projects/my-proj/messages/msg-123"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType to listOf("application/json"))
            )
        }

        val client = HttpClient(engine)
        val sender = FcmHttpV1PushSender(client, "my-proj", FakeAccessTokenProvider("bearer-secret"))

        val result = sender.sendCheck("target-device-token", "check-456")
        assertEquals(PushResult.Sent, result)
        assertEquals("Bearer bearer-secret", recordedAuthHeader)

        val root = json.parseToJsonElement(recordedBody!!).jsonObject
        val message = root["message"]!!.jsonObject
        assertEquals("target-device-token", message["token"]!!.jsonPrimitive.content)

        val data = message["data"]!!.jsonObject
        assertEquals("check", data["type"]!!.jsonPrimitive.content)
        assertEquals("check-456", data["checkId"]!!.jsonPrimitive.content)

        val android = message["android"]!!.jsonObject
        assertEquals("HIGH", android["priority"]!!.jsonPrimitive.content)
        assertEquals("30s", android["ttl"]!!.jsonPrimitive.content)
    }

    @Test
    fun unregisteredMapsToUnregistered() = runTest {
        val engine404 = MockEngine {
            respond(
                content = """{"error":{"code":404,"message":"Requested entity was not found."}}""",
                status = HttpStatusCode.NotFound,
                headers = headersOf(HttpHeaders.ContentType to listOf("application/json"))
            )
        }
        val sender404 = FcmHttpV1PushSender(HttpClient(engine404), "my-proj", FakeAccessTokenProvider())
        assertEquals(PushResult.Unregistered, sender404.sendCheck("tok", "c1"))

        val engineErrorCode = MockEngine {
            respond(
                content = """{"error":{"details":[{"@type":"type.googleapis.com/google.firebase.fcm.v1.FcmError","errorCode":"UNREGISTERED"}]}}""",
                status = HttpStatusCode.BadRequest,
                headers = headersOf(HttpHeaders.ContentType to listOf("application/json"))
            )
        }
        val senderErrorCode = FcmHttpV1PushSender(HttpClient(engineErrorCode), "my-proj", FakeAccessTokenProvider())
        assertEquals(PushResult.Unregistered, senderErrorCode.sendCheck("tok", "c1"))
    }

    @Test
    fun serverErrorMapsToFailed() = runTest {
        val engine500 = MockEngine {
            respond(
                content = """{"error":"Internal Server Error"}""",
                status = HttpStatusCode.InternalServerError,
                headers = headersOf(HttpHeaders.ContentType to listOf("application/json"))
            )
        }
        val sender500 = FcmHttpV1PushSender(HttpClient(engine500), "my-proj", FakeAccessTokenProvider())
        val result = sender500.sendCheck("tok", "c1")
        assertTrue(result is PushResult.Failed)
    }

    @Test
    fun errorMentioningUnregisteredInMessageIsFailed() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"error":{"code":500,"message":"Found UNREGISTERED in log message but no error code details"}}""",
                status = HttpStatusCode.InternalServerError,
                headers = headersOf(HttpHeaders.ContentType to listOf("application/json"))
            )
        }
        val sender = FcmHttpV1PushSender(HttpClient(engine), "my-proj", FakeAccessTokenProvider())
        val result = sender.sendCheck("tok", "c1")
        assertTrue(result is PushResult.Failed)
    }
}
