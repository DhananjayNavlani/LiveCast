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
    private var stopJob: Job? = null

    override fun start(scope: CoroutineScope) {
        val prev = stopJob
        startJob = scope.launch {
            prev?.join()
            val enrollmentStatus = enrollment.awaitStatus()
            if (enrollmentStatus !is EnrollmentStatus.Enrolled) return@launch
            val deviceId = identity.deviceId ?: return@launch
            writer.writeStatus(
                deviceId = deviceId,
                accessibilityOn = true,
                notificationsOn = status.notificationsOn()
            ).onFailure {
                Log.w("AccessibilityStateReporter", "Failed to report accessibility on for $deviceId", it)
            }
        }
    }

    override fun stop(reportOff: Boolean) {
        startJob?.cancel()
        startJob = null
        if (reportOff) {
            stopJob = appScope.launch {
                val enrollmentStatus = enrollment.status()
                if (enrollmentStatus is EnrollmentStatus.NotEnrolled) return@launch
                val deviceId = identity.deviceId ?: return@launch
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
}
