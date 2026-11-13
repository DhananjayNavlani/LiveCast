package com.dhananjay.livecast.group

import com.dhananjay.livecast.protocol.GroupRules
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class PresenceHeartbeat(
    private val reporter: PresenceReporter,
    private val intervalMs: Long = GroupRules.HEARTBEAT_INTERVAL_MS
) {
    suspend fun run(deviceId: String): Nothing {
        try {
            while (true) {
                coroutineScope {
                    val delayJob = launch { delay(intervalMs) }
                    withTimeoutOrNull(intervalMs) {
                        reporter.report(deviceId, true)
                    }
                    delayJob.join()
                }
            }
        } finally {
            withContext(NonCancellable) {
                withTimeoutOrNull(10_000L) {
                    reporter.report(deviceId, false)
                }
            }
        }
    }
}
