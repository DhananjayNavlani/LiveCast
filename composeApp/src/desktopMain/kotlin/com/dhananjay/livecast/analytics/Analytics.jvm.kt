
package com.dhananjay.livecast.analytics

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Desktop (JVM) implementation that logs to console and optionally to a file.
 * In production, you could integrate with services like Mixpanel, Amplitude, etc.
 */
class DesktopAnalytics : Analytics {
    private val userProperties = mutableMapOf<String, String?>()
    private var userId: String? = null
    private var collectionEnabled = true

    private val analyticsLogFile: File? by lazy {
        try {
            val userHome = System.getProperty("user.home")
            val logDir = File(userHome, ".livecast/logs")
            logDir.mkdirs()
            File(logDir, "analytics.log")
        } catch (_: Exception) {
            null
        }
    }

    override fun logEvent(eventName: String, params: Map<String, Any>?) {
        if (!collectionEnabled) return

        val timestamp = getTimestamp()
        val logMessage = buildString {
            append("[$timestamp] Event: $eventName")
            if (!params.isNullOrEmpty()) {
                append(" | Params: $params")
            }
            userId?.let { append(" | User: $it") }
        }

        println("[Analytics] $logMessage")
        writeToFile(logMessage)
    }

    override fun logScreenView(screenName: String, screenClass: String?) {
        if (!collectionEnabled) return

        val timestamp = getTimestamp()
        val logMessage = buildString {
            append("[$timestamp] Screen View: $screenName")
            screenClass?.let { append(" | Class: $it") }
            userId?.let { append(" | User: $it") }
        }

        println("[Analytics] $logMessage")
        writeToFile(logMessage)
    }

    override fun setUserProperty(name: String, value: String?) {
        userProperties[name] = value
        val logMessage = "User Property Set: $name = $value"
        println("[Analytics] $logMessage")
        writeToFile(logMessage)
    }

    override fun setUserId(userId: String?) {
        this.userId = userId
        val logMessage = "User ID Set: $userId"
        println("[Analytics] $logMessage")
        writeToFile(logMessage)
    }

    override fun setAnalyticsCollectionEnabled(enabled: Boolean) {
        collectionEnabled = enabled
        println("[Analytics] Collection Enabled: $enabled")
    }

    override fun resetAnalyticsData() {
        userProperties.clear()
        userId = null
        println("[Analytics] Data Reset")
        writeToFile("Analytics data reset")
    }

    private fun getTimestamp(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    }

    private fun writeToFile(message: String) {
        try {
            analyticsLogFile?.appendText("${getTimestamp()} - $message\n")
        } catch (_: Exception) {
            // Silently fail if we can't write to file
        }
    }
}

actual fun createAnalytics(): Analytics = DesktopAnalytics()
