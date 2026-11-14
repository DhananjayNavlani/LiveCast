package com.dhananjay.livecast.server

import com.dhananjay.livecast.protocol.GroupRole
import com.dhananjay.livecast.server.check.CheckService
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.options
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RoutesTest {

    private class TestCheckService(var outcome: CheckOutcome) : CheckService(
        verifier = object : IdTokenVerifier {
            override suspend fun verify(idToken: String): VerifiedCaller = VerifiedCaller("u1", "u@e.com")
        },
        devices = object : DeviceDirectory {
            override suspend fun groupOf(deviceId: String): String? = "g1"
            override suspend fun pushTokenOf(deviceId: String): String? = "tok"
        },
        members = object : MembershipDirectory {
            override suspend fun roleOf(groupId: String, email: String): GroupRole? = GroupRole.OWNER
        },
        push = object : PushSender {
            override suspend fun sendCheck(token: String, checkId: String): PushResult = PushResult.Sent
        },
        limiter = object : CheckRateLimiter {
            override fun tryAcquire(callerUid: String, deviceId: String, nowMs: Long): Boolean = true
        },
        ids = object : IdGenerator {
            override fun newId(): String = "c1"
        },
        clock = object : Clock {
            override fun nowMs(): Long = 0L
        }
    ) {
        override suspend fun requestCheck(bearerToken: String?, deviceId: String): CheckOutcome = outcome
    }

    @Test
    fun eachOutcomeMapsToStatusAndBody() = testApplication {
        val checkService = TestCheckService(CheckOutcome.Sent("c-123"))
        application {
            livecastModule(checkService, emptyList())
        }

        // 1. Sent
        checkService.outcome = CheckOutcome.Sent("c-123")
        var resp = client.post("/v1/devices/d1/checks") {
            header(HttpHeaders.Authorization, "Bearer token")
        }
        assertEquals(HttpStatusCode.Accepted, resp.status)
        assertTrue(resp.bodyAsText().contains("c-123"))

        // 2. Unauthorized
        checkService.outcome = CheckOutcome.Unauthorized
        resp = client.post("/v1/devices/d1/checks")
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
        assertTrue(resp.bodyAsText().contains("unauthorized"))

        // 3. DeviceNotFound
        checkService.outcome = CheckOutcome.DeviceNotFound
        resp = client.post("/v1/devices/d1/checks") {
            header(HttpHeaders.Authorization, "Bearer token")
        }
        assertEquals(HttpStatusCode.NotFound, resp.status)
        assertTrue(resp.bodyAsText().contains("device_not_found"))

        // 4. Forbidden
        checkService.outcome = CheckOutcome.Forbidden
        resp = client.post("/v1/devices/d1/checks") {
            header(HttpHeaders.Authorization, "Bearer token")
        }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
        assertTrue(resp.bodyAsText().contains("forbidden"))

        // 5. RateLimited
        checkService.outcome = CheckOutcome.RateLimited
        resp = client.post("/v1/devices/d1/checks") {
            header(HttpHeaders.Authorization, "Bearer token")
        }
        assertEquals(HttpStatusCode.TooManyRequests, resp.status)
        assertTrue(resp.bodyAsText().contains("rate_limited"))

        // 6. NoPushToken
        checkService.outcome = CheckOutcome.NoPushToken
        resp = client.post("/v1/devices/d1/checks") {
            header(HttpHeaders.Authorization, "Bearer token")
        }
        assertEquals(HttpStatusCode.Conflict, resp.status)
        assertTrue(resp.bodyAsText().contains("no_push_token"))

        // 7. AppRemoved
        checkService.outcome = CheckOutcome.AppRemoved
        resp = client.post("/v1/devices/d1/checks") {
            header(HttpHeaders.Authorization, "Bearer token")
        }
        assertEquals(HttpStatusCode.Gone, resp.status)
        assertTrue(resp.bodyAsText().contains("app_removed"))

        // 8. UpstreamFailed
        checkService.outcome = CheckOutcome.UpstreamFailed("timeout")
        resp = client.post("/v1/devices/d1/checks") {
            header(HttpHeaders.Authorization, "Bearer token")
        }
        assertEquals(HttpStatusCode.BadGateway, resp.status)
        assertTrue(resp.bodyAsText().contains("upstream_error"))
    }

    @Test
    fun healthzOk() = testApplication {
        val checkService = TestCheckService(CheckOutcome.Sent("c1"))
        application {
            livecastModule(checkService, emptyList())
        }
        val resp = client.get("/healthz")
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    @Test
    fun corsPreflightAllowedOrigin() = testApplication {
        val checkService = TestCheckService(CheckOutcome.Sent("c1"))
        application {
            livecastModule(checkService, listOf("http://localhost:3000"))
        }

        val resp = client.options("/v1/devices/d1/checks") {
            header(HttpHeaders.Origin, "http://localhost:3000")
            header(HttpHeaders.AccessControlRequestMethod, HttpMethod.Post.value)
            header(HttpHeaders.AccessControlRequestHeaders, HttpHeaders.Authorization)
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        assertEquals("http://localhost:3000", resp.headers[HttpHeaders.AccessControlAllowOrigin])
    }
}
