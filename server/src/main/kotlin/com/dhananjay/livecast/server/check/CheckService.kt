package com.dhananjay.livecast.server.check

import com.dhananjay.livecast.protocol.CheckRules
import com.dhananjay.livecast.protocol.GroupRole
import com.dhananjay.livecast.server.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

open class CheckService(
    private val verifier: IdTokenVerifier,
    private val devices: DeviceDirectory,
    private val members: MembershipDirectory,
    private val push: PushSender,
    private val limiter: CheckRateLimiter,
    private val ids: IdGenerator,
    private val clock: Clock,
) {
    open suspend fun requestCheck(bearerToken: String?, deviceId: String): CheckOutcome {
        try {
            if (bearerToken.isNullOrBlank()) return CheckOutcome.Unauthorized
            val token = if (bearerToken.startsWith("Bearer ", ignoreCase = true)) {
                bearerToken.substring(7).trim()
            } else {
                bearerToken.trim()
            }
            if (token.isEmpty()) return CheckOutcome.Unauthorized

            val caller = verifier.verify(token) ?: return CheckOutcome.Unauthorized

            val groupId = devices.groupOf(deviceId) ?: return CheckOutcome.DeviceNotFound

            val role = members.roleOf(groupId, caller.email.trim().lowercase())
            if (role != GroupRole.OWNER && role != GroupRole.SUPERVISOR) {
                return CheckOutcome.Forbidden
            }

            if (!limiter.tryAcquire(caller.uid, deviceId, clock.nowMs())) {
                return CheckOutcome.RateLimited
            }

            val pushToken = devices.pushTokenOf(deviceId) ?: return CheckOutcome.NoPushToken

            val checkId = ids.newId()
            return when (val pushResult = push.sendCheck(pushToken, checkId)) {
                is PushResult.Sent -> CheckOutcome.Sent(checkId)
                is PushResult.Unregistered -> CheckOutcome.AppRemoved
                is PushResult.Failed -> CheckOutcome.UpstreamFailed(pushResult.reason)
            }
        } catch (e: UpstreamException) {
            return CheckOutcome.UpstreamFailed(e.message ?: "upstream error")
        }
    }
}

class InMemoryCheckRateLimiter(
    private val minIntervalMs: Long = CheckRules.MIN_INTERVAL_MS
) : CheckRateLimiter {
    private val lastAcquired = ConcurrentHashMap<Pair<String, String>, Long>()

    override fun tryAcquire(callerUid: String, deviceId: String, nowMs: Long): Boolean {
        val key = Pair(callerUid, deviceId)
        var acquired = false
        lastAcquired.compute(key) { _, prevMs ->
            if (prevMs == null || nowMs - prevMs >= minIntervalMs) {
                acquired = true
                nowMs
            } else {
                acquired = false
                prevMs
            }
        }
        return acquired
    }
}

class UuidIdGenerator : IdGenerator {
    override fun newId(): String = UUID.randomUUID().toString()
}

class SystemClock : Clock {
    override fun nowMs(): Long = System.currentTimeMillis()
}
