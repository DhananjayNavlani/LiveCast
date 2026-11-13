package com.dhananjay.livecast.client.pairing

import com.dhananjay.livecast.auth.AuthService
import com.dhananjay.livecast.protocol.HostDevice
import com.dhananjay.livecast.protocol.PairingRequest
import kotlinx.coroutines.flow.Flow

data class SubmittedRequest(
    val request: PairingRequest,
    val codeExpiresAtEpochMs: Long
)

interface ClientPairingRepository {
    suspend fun submitCode(code: String): Result<SubmittedRequest>
    fun observeRequest(requestId: String): Flow<PairingRequest?>
    fun observeMyDevices(): Flow<List<HostDevice>>
    suspend fun removeDevice(deviceId: String): Result<Unit>
}

sealed class PairingError(message: String) : Exception(message) {
    data object InvalidOrExpiredCode : PairingError("Code invalid or expired")
    data object AnonymousNotAllowed : PairingError("Sign in with an account to add a device")
    data object NotSignedIn : PairingError("Not signed in")
    open class Unsupported(message: String = "Pairing is not available on this platform yet") : PairingError(message) {
        companion object : Unsupported("Pairing is not available on this platform yet")
    }
}

expect fun createClientPairingRepository(authService: AuthService): ClientPairingRepository
