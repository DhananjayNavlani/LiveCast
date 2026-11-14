package com.dhananjay.livecast.agent

import android.util.Log
import com.dhananjay.livecast.group.DeviceIdentity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class AccessibilityStateReporter(
    private val identity: DeviceIdentity,
    private val enrollment: EnrollmentState,
    private val status: DeviceStatusSource,
    private val writer: DeviceStatusWriter,
    private val appScope: CoroutineScope
) : AgentComponent {

    private var startJob: Job? = null

    override fun start(scope: CoroutineScope) {
        startJob = scope.launch {
            val deviceId = identity.deviceId ?: return@launch
            if (enrollment.currentGroupId() == null) return@launch
            writer.writeStatus(
                deviceId = deviceId,
                accessibilityOn = true,
                notificationsOn = status.notificationsOn()
            ).onFailure {
                Log.w("AccessibilityStateReporter", "Failed to report accessibility on for $deviceId", it)
            }
        }
    }

    override fun stop() {
        startJob?.cancel()
        startJob = null
        appScope.launch {
            val deviceId = identity.deviceId ?: return@launch
            if (enrollment.currentGroupId() == null) return@launch
            writer.writeStatus(
                deviceId = deviceId,
                accessibilityOn = false,
                notificationsOn = status.notificationsOn()
            ).onFailure {
                Log.w("AccessibilityStateReporter", "Failed to report accessibility off for $deviceId", it)
            }
        }
    }
}
