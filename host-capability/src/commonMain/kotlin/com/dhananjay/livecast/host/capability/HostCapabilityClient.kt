package com.dhananjay.livecast.host.capability

import com.dhananjay.livecast.core.LiveCastError
import com.dhananjay.livecast.core.LiveCastResult
import com.dhananjay.livecast.protocol.DeviceState

interface HostCapabilityClient {
    val supported: Set<HostCapability>

    suspend fun deviceState(): LiveCastResult<DeviceState> =
        LiveCastResult.Failure(LiveCastError("unsupported"))

    suspend fun lock(): LiveCastResult<Unit> =
        LiveCastResult.Failure(LiveCastError("unsupported"))

    suspend fun locate(): LiveCastResult<Unit> =
        LiveCastResult.Failure(LiveCastError("unsupported"))

    suspend fun wipe(): LiveCastResult<Unit> =
        LiveCastResult.Failure(LiveCastError("unsupported"))
}
