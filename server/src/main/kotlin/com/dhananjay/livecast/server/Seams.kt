package com.dhananjay.livecast.server

import com.dhananjay.livecast.protocol.GroupRole
import java.security.interfaces.RSAPublicKey

data class VerifiedCaller(val uid: String, val email: String)

interface IdTokenVerifier {
    suspend fun verify(idToken: String): VerifiedCaller?
}

interface DeviceDirectory {
    suspend fun groupOf(deviceId: String): String?
    suspend fun pushTokenOf(deviceId: String): String?
}

interface MembershipDirectory {
    suspend fun roleOf(groupId: String, email: String): GroupRole?
}

sealed interface PushResult {
    data object Sent : PushResult
    data object Unregistered : PushResult
    data class Failed(val reason: String) : PushResult
}

interface PushSender {
    suspend fun sendCheck(token: String, checkId: String): PushResult
}

interface CheckRateLimiter {
    fun tryAcquire(callerUid: String, deviceId: String, nowMs: Long): Boolean
}

interface IdGenerator {
    fun newId(): String
}

interface Clock {
    fun nowMs(): Long
}

class UpstreamException(message: String, cause: Throwable? = null) : Exception(message, cause)

sealed interface CheckOutcome {
    data class Sent(val checkId: String) : CheckOutcome
    data object Unauthorized : CheckOutcome
    data object DeviceNotFound : CheckOutcome
    data object Forbidden : CheckOutcome
    data object RateLimited : CheckOutcome
    data object NoPushToken : CheckOutcome
    data object AppRemoved : CheckOutcome
    data class UpstreamFailed(val reason: String) : CheckOutcome
}

interface PublicKeySource {
    suspend fun keys(): Map<String, RSAPublicKey>
}

interface AccessTokenProvider {
    suspend fun token(): String
}

typealias CheckService = com.dhananjay.livecast.server.check.CheckService
typealias InMemoryCheckRateLimiter = com.dhananjay.livecast.server.check.InMemoryCheckRateLimiter

