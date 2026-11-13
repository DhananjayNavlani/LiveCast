package com.dhananjay.livecast.client.pairing

import com.dhananjay.livecast.protocol.HostDevice
import com.dhananjay.livecast.protocol.PairingRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

data class SubmittedRequest(
    val request: PairingRequest,
    val codeExpiresAtEpochMs: Long
)

interface PairingRequestRepository {
    suspend fun submitCode(code: String): Result<SubmittedRequest>
    fun observeRequest(requestId: String): Flow<PairingRequest?>
}

interface SupervisedDeviceRepository {
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

class UnsupportedPairingRequestRepository(
    private val message: String = "Pairing is not available on this platform yet"
) : PairingRequestRepository {
    override suspend fun submitCode(code: String): Result<SubmittedRequest> {
        return Result.failure(PairingError.Unsupported(message))
    }

    override fun observeRequest(requestId: String): Flow<PairingRequest?> = flowOf(null)
}

class UnsupportedSupervisedDeviceRepository(
    private val message: String = "Pairing is not available on this platform yet"
) : SupervisedDeviceRepository {
    override fun observeMyDevices(): Flow<List<HostDevice>> = flowOf(emptyList())

    override suspend fun removeDevice(deviceId: String): Result<Unit> {
        return Result.failure(PairingError.Unsupported(message))
    }
}
