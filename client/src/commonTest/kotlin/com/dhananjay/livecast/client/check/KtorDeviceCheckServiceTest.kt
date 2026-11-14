package com.dhananjay.livecast.client.check

import com.dhananjay.livecast.auth.IdTokenProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

private class FakeIdTokenProvider(
    var initialToken: String? = "token-1",
    var refreshedToken: String? = "token-2",
) : IdTokenProvider {
    val requestedRefreshFlags = mutableListOf<Boolean>()

    override suspend fun getIdToken(forceRefresh: Boolean): String? {
        requestedRefreshFlags.add(forceRefresh)
        return if (forceRefresh) refreshedToken else initialToken
    }
}

private class FakeServerConfig(override val baseUrl: String?) : ServerConfig

class KtorDeviceCheckServiceTest {

    @Test
    fun requested202ParsesCheckId() = runTest {
        val tokens = FakeIdTokenProvider(initialToken = "secret-token")
        val engine = MockEngine { request ->
            assertEquals("/v1/devices/d1/checks", request.url.encodedPath)
            assertEquals("Bearer secret-token", request.headers[HttpHeaders.Authorization])
            respond(
                content = """{"checkId":"c-123"}""",
                status = HttpStatusCode.Accepted,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val http = HttpClient(engine) {
            install(ContentNegotiation) { json() }
        }
        val service = KtorDeviceCheckService(http, FakeServerConfig("http://localhost:8080"), tokens)

        val result = service.requestCheck("d1")
        assertEquals(CheckRequestResult.Requested("c-123"), result)
    }

    @Test
    fun gone410AppRemoved() = runTest {
        val tokens = FakeIdTokenProvider()
        val engine = MockEngine {
            respond(
                content = """{"status":"app_removed"}""",
                status = HttpStatusCode.Gone,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val http = HttpClient(engine) { install(ContentNegotiation) { json() } }
        val service = KtorDeviceCheckService(http, FakeServerConfig("http://localhost:8080"), tokens)

        val result = service.requestCheck("d1")
        assertEquals(CheckRequestResult.AppRemoved, result)
    }

    @Test
    fun conflict409NoPushToken() = runTest {
        val tokens = FakeIdTokenProvider()
        val engine = MockEngine {
            respond(
                content = """{"status":"no_push_token"}""",
                status = HttpStatusCode.Conflict,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val http = HttpClient(engine) { install(ContentNegotiation) { json() } }
        val service = KtorDeviceCheckService(http, FakeServerConfig("http://localhost:8080"), tokens)

        val result = service.requestCheck("d1")
        assertEquals(CheckRequestResult.NoPushToken, result)
    }

    @Test
    fun forbidden403NotAllowed() = runTest {
        val tokens = FakeIdTokenProvider()
        val engine = MockEngine {
            respond(
                content = """{"status":"forbidden"}""",
                status = HttpStatusCode.Forbidden,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val http = HttpClient(engine) { install(ContentNegotiation) { json() } }
        val service = KtorDeviceCheckService(http, FakeServerConfig("http://localhost:8080"), tokens)

        val result = service.requestCheck("d1")
        assertEquals(CheckRequestResult.NotAllowed, result)
    }

    @Test
    fun tooMany429RateLimited() = runTest {
        val tokens = FakeIdTokenProvider()
        val engine = MockEngine {
            respond(
                content = """{"status":"rate_limited"}""",
                status = HttpStatusCode.TooManyRequests,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val http = HttpClient(engine) { install(ContentNegotiation) { json() } }
        val service = KtorDeviceCheckService(http, FakeServerConfig("http://localhost:8080"), tokens)

        val result = service.requestCheck("d1")
        assertEquals(CheckRequestResult.RateLimited, result)
    }

    @Test
    fun server500Failed() = runTest {
        val tokens = FakeIdTokenProvider()
        val engine = MockEngine {
            respond(
                content = "Internal Server Error",
                status = HttpStatusCode.InternalServerError
            )
        }
        val http = HttpClient(engine)
        val service = KtorDeviceCheckService(http, FakeServerConfig("http://localhost:8080"), tokens)

        val result = service.requestCheck("d1")
        assertEquals(CheckRequestResult.Failed("Check failed (HTTP 500)"), result)
    }

    @Test
    fun badGateway502FixedMessage() = runTest {
        val tokens = FakeIdTokenProvider()
        val engine = MockEngine {
            respond(
                content = """{"status":"upstream_error"}""",
                status = HttpStatusCode.BadGateway,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val http = HttpClient(engine)
        val service = KtorDeviceCheckService(http, FakeServerConfig("http://localhost:8080"), tokens)

        val result = service.requestCheck("d1")
        assertEquals(CheckRequestResult.Failed("Check failed (HTTP 502)"), result)
    }

    @Test
    fun malformed202FixedMessage() = runTest {
        val tokens = FakeIdTokenProvider()
        val engine = MockEngine {
            respond(
                content = "not json",
                status = HttpStatusCode.Accepted
            )
        }
        val http = HttpClient(engine)
        val service = KtorDeviceCheckService(http, FakeServerConfig("http://localhost:8080"), tokens)

        val result = service.requestCheck("d1")
        assertEquals(CheckRequestResult.Failed("Check failed"), result)
    }

    @Test
    fun requestTimeoutFailed() = runTest {
        val tokens = FakeIdTokenProvider()
        val engine = MockEngine {
            delay(200)
            respond("OK", HttpStatusCode.Accepted)
        }
        val http = HttpClient(engine) {
            install(HttpTimeout) {
                requestTimeoutMillis = 50
            }
        }
        val service = KtorDeviceCheckService(http, FakeServerConfig("http://localhost:8080"), tokens)

        val result = service.requestCheck("d1")
        assertEquals(CheckRequestResult.Failed("Check failed"), result)
    }

    @Test
    fun unauthorizedRetriesOnceWithFreshToken() = runTest {
        val tokens = FakeIdTokenProvider(initialToken = "old-tok", refreshedToken = "fresh-tok")
        var requestCount = 0
        val engine = MockEngine { request ->
            requestCount++
            if (requestCount == 1) {
                assertEquals("Bearer old-tok", request.headers[HttpHeaders.Authorization])
                respond(
                    content = """{"status":"unauthorized"}""",
                    status = HttpStatusCode.Unauthorized,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else {
                assertEquals("Bearer fresh-tok", request.headers[HttpHeaders.Authorization])
                respond(
                    content = """{"checkId":"c-fresh"}""",
                    status = HttpStatusCode.Accepted,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        }
        val http = HttpClient(engine)
        val service = KtorDeviceCheckService(http, FakeServerConfig("http://localhost:8080"), tokens)

        val result = service.requestCheck("d1")
        assertEquals(CheckRequestResult.Requested("c-fresh"), result)
        assertEquals(2, requestCount)
        assertEquals(listOf(false, true), tokens.requestedRefreshFlags)
    }

    @Test
    fun missingBaseUrlFailsWithoutRequest() = runTest {
        val tokens = FakeIdTokenProvider()
        var requestsMade = 0
        val engine = MockEngine {
            requestsMade++
            respond("OK", HttpStatusCode.OK)
        }
        val http = HttpClient(engine)
        val service = KtorDeviceCheckService(http, FakeServerConfig(null), tokens)

        val result = service.requestCheck("d1")
        assertEquals(CheckRequestResult.Failed("Server not configured"), result)
        assertEquals(0, requestsMade)
    }

    @Test
    fun networkErrorFailed() = runTest {
        val tokens = FakeIdTokenProvider()
        val engine = MockEngine {
            throw Exception("Connection refused")
        }
        val http = HttpClient(engine)
        val service = KtorDeviceCheckService(http, FakeServerConfig("http://localhost:8080"), tokens)

        val result = service.requestCheck("d1")
        assertEquals(CheckRequestResult.Failed("Check failed"), result)
    }

    @Test
    fun httpsAllowed() {
        assertTrue(isAllowedServerUrl("https://api.livecast.com"))
    }

    @Test
    fun httpRemoteRejected() = runTest {
        assertFalse(isAllowedServerUrl("http://api.livecast.com"))

        val tokens = FakeIdTokenProvider()
        var requestsMade = 0
        val engine = MockEngine {
            requestsMade++
            respond("OK", HttpStatusCode.OK)
        }
        val http = HttpClient(engine)
        val service = KtorDeviceCheckService(http, FakeServerConfig("http://api.livecast.com"), tokens)

        val result = service.requestCheck("d1")
        assertEquals(CheckRequestResult.Failed("Server not configured"), result)
        assertEquals(0, requestsMade)
    }

    @Test
    fun httpLocalhostAllowed() {
        assertTrue(isAllowedServerUrl("http://localhost:8080"))
    }

    @Test
    fun httpEmulatorHostAllowed() {
        assertTrue(isAllowedServerUrl("http://10.0.2.2:8080"))
    }
}
