package com.dhananjay.livecast.agent

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import com.dhananjay.livecast.cast.data.services.AccessibilityService

interface DeviceStatusSource {
    fun accessibilityOn(): Boolean
    fun notificationsOn(): Boolean
}

data class ComponentNameStrings(
    val full: String,
    val short: String
)

object AccessibilitySettingParser {
    fun isEnabled(
        enabledServices: String?,
        accessibilityEnabled: Int,
        component: ComponentNameStrings
    ): Boolean {
        if (accessibilityEnabled != 1 || enabledServices.isNullOrBlank()) {
            return false
        }
        val items = enabledServices.split(":")
        return items.any { item ->
            val trimmed = item.trim()
            trimmed == component.full || trimmed == component.short
        }
    }
}

class AndroidDeviceStatusSource(
    private val context: Context
) : DeviceStatusSource {

    override fun accessibilityOn(): Boolean {
        val accessibilityEnabled = try {
            Settings.Secure.getInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0)
        } catch (_: Exception) {
            0
        }
        val enabledServices = try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        } catch (_: Exception) {
            null
        }
        val componentName = ComponentName(context, AccessibilityService::class.java)
        val component = ComponentNameStrings(
            full = componentName.flattenToString(),
            short = componentName.flattenToShortString()
        )
        return AccessibilitySettingParser.isEnabled(enabledServices, accessibilityEnabled, component)
    }

    override fun notificationsOn(): Boolean {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return false
        return notificationManager.areNotificationsEnabled()
    }
}
