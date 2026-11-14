package com.dhananjay.livecast.client.check

import com.dhananjay.livecast.protocol.HostDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withTimeoutOrNull

suspend fun awaitCheckReply(
    devices: Flow<List<HostDevice>>,
    deviceId: String,
    checkId: String,
    timeoutMs: Long
): Long? {
    return withTimeoutOrNull(timeoutMs) {
        devices
            .mapNotNull { list ->
                val dev = list.firstOrNull { it.id == deviceId }
                if (dev != null && dev.lastCheckId == checkId) {
                    dev.lastCheckAtEpochMs ?: 0L
                } else {
                    null
                }
            }
            .first()
    }
}
