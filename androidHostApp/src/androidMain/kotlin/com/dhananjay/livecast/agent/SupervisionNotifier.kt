package com.dhananjay.livecast.agent

import android.util.Log
import com.dhananjay.livecast.group.GroupMembersDirectory
import com.dhananjay.livecast.protocol.GroupRole
import com.dhananjay.livecast.protocol.GroupRules
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SupervisionNotifier(
    private val enrollment: EnrollmentState,
    private val members: GroupMembersDirectory,
    private val notifications: AgentNotifications,
    private val appScope: CoroutineScope
) : AgentComponent {

    private var startJob: Job? = null

    override fun start(scope: CoroutineScope) {
        startJob = scope.launch {
            val groupId = enrollment.currentGroupId() ?: return@launch
            try {
                val supervisors = members.observeSupervisors(groupId).first()
                val owner = supervisors.firstOrNull { it.role == GroupRole.OWNER }
                if (owner != null) {
                    val name = GroupRules.displayName(owner)
                    notifications.showSupervised(name)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.w("SupervisionNotifier", "Failed to resolve owner for group $groupId", t)
            }
        }
    }

    override fun stop() {
        startJob?.cancel()
        startJob = null
        appScope.launch {
            val groupId = enrollment.currentGroupId() ?: return@launch
            notifications.showAccessibilityOff()
        }
    }
}
