package com.dhananjay.livecast.agent

import android.util.Log
import com.dhananjay.livecast.group.GroupMembersDirectory
import com.dhananjay.livecast.protocol.GroupRole
import com.dhananjay.livecast.protocol.GroupRules
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SupervisionNotifier(
    private val enrollment: EnrollmentState,
    private val members: GroupMembersDirectory,
    private val notifications: AgentNotifications,
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
            val groupId = enrollmentStatus.groupId

            // Before the first name is known, notifications.cancel() any stale off notice
            notifications.cancel()

            val retryDelaysMs = listOf(15_000L, 30_000L, 60_000L, 120_000L, 240_000L)
            var attempt = 0
            while (isActive) {
                try {
                    members.observeSupervisors(groupId).collect { supervisors ->
                        if (supervisors.isEmpty()) return@collect
                        val chosen = supervisors.firstOrNull { it.role == GroupRole.OWNER }
                            ?: supervisors.first()
                        notifications.showSupervised(GroupRules.displayName(chosen))
                    }
                    break
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    Log.w("SupervisionNotifier", "Error observing supervisors for group $groupId", t)
                    if (attempt < retryDelaysMs.size) {
                        delay(retryDelaysMs[attempt])
                        attempt++
                    } else {
                        break
                    }
                }
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
                notifications.showAccessibilityOff()
            }
        }
    }
}
