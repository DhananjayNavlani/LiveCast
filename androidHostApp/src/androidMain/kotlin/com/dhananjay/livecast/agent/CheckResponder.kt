package com.dhananjay.livecast.agent

import android.util.Log
import com.dhananjay.livecast.group.DeviceIdentity
import com.dhananjay.livecast.protocol.CheckRules
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

object CheckMessageParser {
    fun checkIdOf(data: Map<String, String>): String? {
        val type = data[CheckRules.DATA_TYPE]
        if (type != CheckRules.TYPE_CHECK) return null
        val checkId = data[CheckRules.DATA_CHECK_ID]
        return if (checkId.isNullOrBlank()) null else checkId
    }
}

class CheckResponder(
    private val identity: DeviceIdentity,
    private val status: DeviceStatusSource,
    private val writer: DeviceStatusWriter,
    private val notifications: AgentNotifications,
    private val timeoutMs: Long = 10_000L
) {
    suspend fun respond(checkId: String): Result<Unit> {
        val deviceId = identity.deviceId
            ?: return Result.failure(IllegalStateException("No device ID"))

        val accessibility = status.accessibilityOn()
        val notificationsOn = status.notificationsOn()

        if (!accessibility) {
            runCatching {
                notifications.showAccessibilityOff()
            }.onFailure { t ->
                Log.w("CheckResponder", "Failed to show accessibility off notice", t)
            }
        }

        return try {
            withTimeout(timeoutMs) {
                writer.writeCheckReply(
                    deviceId = deviceId,
                    checkId = checkId,
                    accessibilityOn = accessibility,
                    notificationsOn = notificationsOn
                )
            }
        } catch (e: TimeoutCancellationException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
