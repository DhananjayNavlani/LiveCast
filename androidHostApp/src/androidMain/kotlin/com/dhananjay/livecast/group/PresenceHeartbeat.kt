package com.dhananjay.livecast.group

import com.dhananjay.livecast.protocol.GroupRules
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class PresenceHeartbeat(
    private val reporter: PresenceReporter,
    private val intervalMs: Long = GroupRules.HEARTBEAT_INTERVAL_MS
) {
    suspend fun run(deviceId: String): Nothing {
        try {
            while (true) {
                reporter.report(deviceId, true)
                delay(intervalMs)
            }
        } finally {
            withContext(NonCancellable) {
                reporter.report(deviceId, false)
            }
        }
    }
}
