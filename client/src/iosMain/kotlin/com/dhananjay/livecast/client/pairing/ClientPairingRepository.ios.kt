package com.dhananjay.livecast.client.pairing

import com.dhananjay.livecast.auth.AuthService
import com.dhananjay.livecast.protocol.HostDevice
import com.dhananjay.livecast.protocol.PairingRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class IosClientPairingRepository : ClientPairingRepository {
    override suspend fun submitCode(code: String): Result<SubmittedRequest> {
        return Result.failure(PairingError.Unsupported)
    }

    override fun observeRequest(requestId: String): Flow<PairingRequest?> = flowOf(null)

    override fun observeMyDevices(): Flow<List<HostDevice>> = flowOf(emptyList())

    override suspend fun removeDevice(deviceId: String): Result<Unit> {
        return Result.failure(PairingError.Unsupported)
    }
}

actual fun createClientPairingRepository(authService: AuthService): ClientPairingRepository =
    IosClientPairingRepository()
