package com.dhananjay.livecast.analytics

import platform.Foundation.NSLog

/**
 * iOS implementation that logs to NSLog.
 * In production, integrate with Firebase Analytics or other analytics services.
 */
class IosAnalytics : Analytics {
    private val userProperties = mutableMapOf<String, String?>()
    private var userId: String? = null
    private var collectionEnabled = true

    override fun logEvent(eventName: String, params: Map<String, Any>?) {
        if (!collectionEnabled) return

        val logMessage = buildString {
            append("[Analytics] Event: $eventName")
            if (!params.isNullOrEmpty()) {
                append(" | Params: $params")
            }
        }
        NSLog(logMessage)
    }

    override fun logScreenView(screenName: String, screenClass: String?) {
        if (!collectionEnabled) return

        val logMessage = buildString {
            append("[Analytics] Screen View: $screenName")
            screenClass?.let { append(" | Class: $it") }
        }
        NSLog(logMessage)
    }

    override fun setUserProperty(name: String, value: String?) {
        userProperties[name] = value
        NSLog("[Analytics] User Property Set: $name = $value")
    }

    override fun setUserId(userId: String?) {
        this.userId = userId
        NSLog("[Analytics] User ID Set: $userId")
    }

    override fun setAnalyticsCollectionEnabled(enabled: Boolean) {
        collectionEnabled = enabled
        NSLog("[Analytics] Collection Enabled: $enabled")
    }

    override fun resetAnalyticsData() {
        userProperties.clear()
        userId = null
        NSLog("[Analytics] Data Reset")
    }
}

/**
 * Factory function to create the iOS analytics implementation.
 */
actual fun createAnalytics(): Analytics = IosAnalytics()

