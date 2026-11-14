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

    private class FakeVerifier : IdTokenVerifier {
        var caller: VerifiedCaller? = VerifiedCaller("u1", "u@e.com")
        var lastToken: String? = null
        override suspend fun verify(idToken: String): VerifiedCaller? {
            lastToken = idToken
            return caller
        }
    }

    private class FakeDevices : DeviceDirectory {
        var groupId: String? = "g1"
        var pushToken: String? = "tok"
        var throwError: Boolean = false
        override suspend fun groupOf(deviceId: String): String? {
            if (throwError) throw UpstreamException("timeout")
            return groupId
        }
        override suspend fun pushTokenOf(deviceId: String): String? = pushToken
    }

    private class FakeMembers : MembershipDirectory {
        var role: GroupRole? = GroupRole.OWNER
        override suspend fun roleOf(groupId: String, email: String): GroupRole? = role
    }

    private class FakePush : PushSender {
        var result: PushResult = PushResult.Sent
        override suspend fun sendCheck(token: String, checkId: String): PushResult = result
    }

    private class FakeLimiter : CheckRateLimiter {
        var allow: Boolean = true
        override fun tryAcquire(callerUid: String, deviceId: String, nowMs: Long): Boolean = allow
    }

    private class FakeIds : IdGenerator {
        var id: String = "c-123"
        override fun newId(): String = id
    }

    private class FakeClock : Clock {
        override fun nowMs(): Long = 0L
    }

    @Test
    fun eachOutcomeMapsToStatusAndBody() = testApplication {
        val verifier = FakeVerifier()
        val devices = FakeDevices()
        val members = FakeMembers()
        val push = FakePush()
        val limiter = FakeLimiter()
        val ids = FakeIds()
        val clock = FakeClock()

        val checkService = CheckService(verifier, devices, members, push, limiter, ids, clock)

        application {
            livecastModule(checkService, emptyList())
        }

        // 1. Sent
        var resp = client.post("/v1/devices/d1/checks") {
            header(HttpHeaders.Authorization, "Bearer test-token-123")
        }
        assertEquals(HttpStatusCode.Accepted, resp.status)
        assertTrue(resp.bodyAsText().contains("c-123"))
        assertEquals("test-token-123", verifier.lastToken)

        // 2. Unauthorized - no header
        val respNoHeader = client.post("/v1/devices/d1/checks")
        assertEquals(HttpStatusCode.Unauthorized, respNoHeader.status)
        assertTrue(respNoHeader.bodyAsText().contains("unauthorized"))

        // 2. Unauthorized - invalid token
        verifier.caller = null
        val respInvalid = client.post("/v1/devices/d1/checks") {
            header(HttpHeaders.Authorization, "Bearer bad-token-xyz")
        }
        assertEquals(HttpStatusCode.Unauthorized, respInvalid.status)
        assertTrue(respInvalid.bodyAsText().contains("unauthorized"))
        assertEquals("bad-token-xyz", verifier.lastToken)

        // 3. DeviceNotFound
        verifier.caller = VerifiedCaller("u1", "u@e.com")
        devices.groupId = null
        resp = client.post("/v1/devices/d1/checks") {
            header(HttpHeaders.Authorization, "Bearer test-token-123")
        }
        assertEquals(HttpStatusCode.NotFound, resp.status)
        assertTrue(resp.bodyAsText().contains("device_not_found"))

        // 4. Forbidden
        devices.groupId = "g1"
        members.role = GroupRole.DEVICE
        resp = client.post("/v1/devices/d1/checks") {
            header(HttpHeaders.Authorization, "Bearer test-token-123")
        }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
        assertTrue(resp.bodyAsText().contains("forbidden"))

        // 5. RateLimited
        members.role = GroupRole.OWNER
        limiter.allow = false
        resp = client.post("/v1/devices/d1/checks") {
            header(HttpHeaders.Authorization, "Bearer test-token-123")
        }
        assertEquals(HttpStatusCode.TooManyRequests, resp.status)
        assertTrue(resp.bodyAsText().contains("rate_limited"))

        // 6. NoPushToken
        limiter.allow = true
        devices.pushToken = null
        resp = client.post("/v1/devices/d1/checks") {
            header(HttpHeaders.Authorization, "Bearer test-token-123")
        }
        assertEquals(HttpStatusCode.Conflict, resp.status)
        assertTrue(resp.bodyAsText().contains("no_push_token"))

        // 7. AppRemoved
        devices.pushToken = "tok"
        push.result = PushResult.Unregistered
        resp = client.post("/v1/devices/d1/checks") {
            header(HttpHeaders.Authorization, "Bearer test-token-123")
        }
        assertEquals(HttpStatusCode.Gone, resp.status)
        assertTrue(resp.bodyAsText().contains("app_removed"))

        // 8. UpstreamFailed
        push.result = PushResult.Sent
        devices.throwError = true
        resp = client.post("/v1/devices/d1/checks") {
            header(HttpHeaders.Authorization, "Bearer test-token-123")
        }
        assertEquals(HttpStatusCode.BadGateway, resp.status)
        assertTrue(resp.bodyAsText().contains("upstream_error"))
    }

    @Test
    fun healthzOk() = testApplication {
        val checkService = CheckService(
            FakeVerifier(), FakeDevices(), FakeMembers(), FakePush(), FakeLimiter(), FakeIds(), FakeClock()
        )
        application {
            livecastModule(checkService, emptyList())
        }
        val resp = client.get("/healthz")
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    @Test
    fun corsPreflightAllowedOrigin() = testApplication {
        val checkService = CheckService(
            FakeVerifier(), FakeDevices(), FakeMembers(), FakePush(), FakeLimiter(), FakeIds(), FakeClock()
        )
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
