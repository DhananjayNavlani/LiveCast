package com.dhananjay.livecast.agent

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

interface EnrollmentListener {
    suspend fun onEnrolled(deviceId: String, groupId: String)
}

interface EnrollmentNotifier {
    fun notifyEnrolled(deviceId: String, groupId: String)
}

class AppScopeEnrollmentNotifier(
    private val listeners: List<EnrollmentListener>,
    private val scope: CoroutineScope
) : EnrollmentNotifier {

    override fun notifyEnrolled(deviceId: String, groupId: String) {
        scope.launch {
            for (listener in listeners) {
                try {
                    listener.onEnrolled(deviceId, groupId)
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    Log.w("EnrollmentNotifier", "Listener ${listener::class.simpleName} failed for $deviceId", t)
                }
            }
        }
    }
}
