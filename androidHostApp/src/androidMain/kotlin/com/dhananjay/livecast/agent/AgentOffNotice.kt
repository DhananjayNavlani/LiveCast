package com.dhananjay.livecast.agent

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named

class AgentOffNoticeChecker(
    private val enrollment: EnrollmentState,
    private val status: DeviceStatusSource,
    private val notifications: AgentNotifications
) {
    suspend fun check() {
        val enrollmentStatus = enrollment.status()
        if (enrollmentStatus is EnrollmentStatus.NotEnrolled) return
        if (!status.accessibilityOn()) {
            notifications.showAccessibilityOff()
        }
    }
}

class AgentOffNoticeReceiver : BroadcastReceiver(), KoinComponent {
    private val checker by inject<AgentOffNoticeChecker>()
    private val appScope by inject<CoroutineScope>(named("appScope"))

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }
        val pendingResult = goAsync()
        appScope.launch {
            try {
                withTimeoutOrNull(10_000L) {
                    checker.check()
                }
            } catch (t: Throwable) {
                Log.w("AgentOffNoticeReceiver", "Failed to check off notice", t)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
