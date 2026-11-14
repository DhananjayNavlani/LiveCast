package com.dhananjay.livecast.server.check

import com.dhananjay.livecast.protocol.GroupRole
import com.dhananjay.livecast.server.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CheckServiceTest {

    private class FakeVerifier(var caller: VerifiedCaller? = VerifiedCaller("u1", "user@example.com")) : IdTokenVerifier {
        var lastToken: String? = null
        override suspend fun verify(idToken: String): VerifiedCaller? {
            lastToken = idToken
            return caller
        }
    }

    private class FakeDeviceDirectory(
        var groups: MutableMap<String, String?> = mutableMapOf("d1" to "g1"),
        var tokens: MutableMap<String, String?> = mutableMapOf("d1" to "token-123"),
        var throwOnGroup: Boolean = false,
        var throwOnToken: Boolean = false,
    ) : DeviceDirectory {
        override suspend fun groupOf(deviceId: String): String? {
            if (throwOnGroup) throw UpstreamException("Firestore error reading device")
            return groups[deviceId]
        }
        override suspend fun pushTokenOf(deviceId: String): String? {
            if (throwOnToken) throw UpstreamException("Firestore error reading token")
            return tokens[deviceId]
        }
    }

    private class FakeMembershipDirectory(
        var roles: MutableMap<Pair<String, String>, GroupRole?> = mutableMapOf(
            Pair("g1", "user@example.com") to GroupRole.OWNER
        ),
        var throwOnRole: Boolean = false,
    ) : MembershipDirectory {
        var lastLookup: Pair<String, String>? = null
        override suspend fun roleOf(groupId: String, email: String): GroupRole? {
            if (throwOnRole) throw UpstreamException("Firestore error reading role")
            lastLookup = Pair(groupId, email)
            return roles[Pair(groupId, email)]
        }
    }

    private class FakePushSender(var result: PushResult = PushResult.Sent) : PushSender {
        var lastToken: String? = null
        var lastCheckId: String? = null
        override suspend fun sendCheck(token: String, checkId: String): PushResult {
            lastToken = token
            lastCheckId = checkId
            return result
        }
    }

    private class FakeRateLimiter(var allow: Boolean = true) : CheckRateLimiter {
        val calls = mutableListOf<Triple<String, String, Long>>()
        override fun tryAcquire(callerUid: String, deviceId: String, nowMs: Long): Boolean {
            calls.add(Triple(callerUid, deviceId, nowMs))
            return allow
        }
    }

    private class FakeIdGenerator(var nextId: String = "c1") : IdGenerator {
        override fun newId(): String = nextId
    }

    private class FakeClock(var now: Long = 100_000L) : Clock {
        override fun nowMs(): Long = now
    }

    private fun createService(
        verifier: IdTokenVerifier = FakeVerifier(),
        devices: DeviceDirectory = FakeDeviceDirectory(),
        members: MembershipDirectory = FakeMembershipDirectory(),
        push: PushSender = FakePushSender(),
        limiter: CheckRateLimiter = FakeRateLimiter(),
        ids: IdGenerator = FakeIdGenerator(),
        clock: Clock = FakeClock(),
    ): CheckService = CheckService(verifier, devices, members, push, limiter, ids, clock)

    @Test
    fun missingBearerUnauthorized() = runTest {
        val service = createService()
        assertEquals(CheckOutcome.Unauthorized, service.requestCheck(null, "d1"))
        assertEquals(CheckOutcome.Unauthorized, service.requestCheck("", "d1"))
        assertEquals(CheckOutcome.Unauthorized, service.requestCheck("   ", "d1"))
    }

    @Test
    fun invalidTokenUnauthorized() = runTest {
        val verifier = FakeVerifier(caller = null)
        val service = createService(verifier = verifier)
        assertEquals(CheckOutcome.Unauthorized, service.requestCheck("Bearer bad-token", "d1"))
        assertEquals("bad-token", verifier.lastToken)
    }

    @Test
    fun unknownDeviceNotFound() = runTest {
        val devices = FakeDeviceDirectory(groups = mutableMapOf())
        val service = createService(devices = devices)
        assertEquals(CheckOutcome.DeviceNotFound, service.requestCheck("Bearer valid", "unknown-device"))
    }

    @Test
    fun unenrolledDeviceNotFound() = runTest {
        val devices = FakeDeviceDirectory(groups = mutableMapOf("d1" to null))
        val service = createService(devices = devices)
        assertEquals(CheckOutcome.DeviceNotFound, service.requestCheck("Bearer valid", "d1"))
    }

    @Test
    fun deviceRoleForbidden() = runTest {
        val members = FakeMembershipDirectory(
            roles = mutableMapOf(Pair("g1", "user@example.com") to GroupRole.DEVICE)
        )
        val service = createService(members = members)
        assertEquals(CheckOutcome.Forbidden, service.requestCheck("Bearer valid", "d1"))
    }

    @Test
    fun nonMemberForbidden() = runTest {
        val members = FakeMembershipDirectory(roles = mutableMapOf())
        val service = createService(members = members)
        assertEquals(CheckOutcome.Forbidden, service.requestCheck("Bearer valid", "d1"))
    }

    @Test
    fun mixedCaseEmailLooksUpLowercase() = runTest {
        val verifier = FakeVerifier(caller = VerifiedCaller("u1", "Mom@X.com"))
        val members = FakeMembershipDirectory(
            roles = mutableMapOf(Pair("g1", "mom@x.com") to GroupRole.SUPERVISOR)
        )
        val service = createService(verifier = verifier, members = members)
        val result = service.requestCheck("Bearer valid", "d1")
        assertEquals(Pair("g1", "mom@x.com"), members.lastLookup)
        assertTrue(result is CheckOutcome.Sent)
    }

    @Test
    fun rateLimitedSecondCall() = runTest {
        val limiter = InMemoryCheckRateLimiter(minIntervalMs = 10_000L)
        val clock = FakeClock(now = 100_000L)
        val devices = FakeDeviceDirectory(
            groups = mutableMapOf("d1" to "g1", "d2" to "g1"),
            tokens = mutableMapOf("d1" to "token-1", "d2" to "token-2")
        )
        val service = createService(devices = devices, limiter = limiter, clock = clock)

        val first = service.requestCheck("Bearer valid", "d1")
        assertTrue(first is CheckOutcome.Sent)

        // Same caller + device within 10 s -> RateLimited
        clock.now = 105_000L
        val second = service.requestCheck("Bearer valid", "d1")
        assertEquals(CheckOutcome.RateLimited, second)

        // Different device within 10 s -> allowed
        val otherDevice = service.requestCheck("Bearer valid", "d2")
        assertTrue(otherDevice is CheckOutcome.Sent)
    }

    @Test
    fun noTokenConflict() = runTest {
        val devices = FakeDeviceDirectory(tokens = mutableMapOf("d1" to null))
        val service = createService(devices = devices)
        assertEquals(CheckOutcome.NoPushToken, service.requestCheck("Bearer valid", "d1"))
    }

    @Test
    fun sentReturnsCheckId() = runTest {
        val push = FakePushSender(PushResult.Sent)
        val ids = FakeIdGenerator("c1")
        val service = createService(push = push, ids = ids)
        val result = service.requestCheck("Bearer valid", "d1")
        assertEquals(CheckOutcome.Sent("c1"), result)
        assertEquals("token-123", push.lastToken)
        assertEquals("c1", push.lastCheckId)
    }

    @Test
    fun unregisteredAppRemoved() = runTest {
        val push = FakePushSender(PushResult.Unregistered)
        val service = createService(push = push)
        assertEquals(CheckOutcome.AppRemoved, service.requestCheck("Bearer valid", "d1"))
    }

    @Test
    fun pushFailedUpstream() = runTest {
        val push = FakePushSender(PushResult.Failed("network down"))
        val service = createService(push = push)
        assertEquals(CheckOutcome.UpstreamFailed("network down"), service.requestCheck("Bearer valid", "d1"))
    }

    @Test
    fun directoryErrorUpstream() = runTest {
        val devices = FakeDeviceDirectory(throwOnGroup = true)
        val service = createService(devices = devices)
        val result = service.requestCheck("Bearer valid", "d1")
        assertTrue(result is CheckOutcome.UpstreamFailed)
    }
}
