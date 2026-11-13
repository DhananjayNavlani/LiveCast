package com.dhananjay.livecast.analytics

import android.util.Log

/**
 * Android implementation that logs to Logcat.
 * In production, integrate with Firebase Analytics or other analytics services.
 */
class AndroidAnalytics : Analytics {
    private val userProperties = mutableMapOf<String, String?>()
    private var userId: String? = null
    private var collectionEnabled = true

    companion object {
        private const val TAG = "Analytics"
    }

    override fun logEvent(eventName: String, params: Map<String, Any>?) {
        if (!collectionEnabled) return

        val logMessage = buildString {
            append("Event: $eventName")
            if (!params.isNullOrEmpty()) {
                append(" | Params: $params")
            }
        }
        Log.d(TAG, logMessage)
    }

    override fun logScreenView(screenName: String, screenClass: String?) {
        if (!collectionEnabled) return

        val logMessage = buildString {
            append("Screen View: $screenName")
            screenClass?.let { append(" | Class: $it") }
        }
        Log.d(TAG, logMessage)
    }

    override fun setUserProperty(name: String, value: String?) {
        userProperties[name] = value
        Log.d(TAG, "User Property Set: $name = $value")
    }

    override fun setUserId(userId: String?) {
        this.userId = userId
        Log.d(TAG, "User ID Set: $userId")
    }

    override fun setAnalyticsCollectionEnabled(enabled: Boolean) {
        collectionEnabled = enabled
        Log.d(TAG, "Collection Enabled: $enabled")
    }

    override fun resetAnalyticsData() {
        userProperties.clear()
        userId = null
        Log.d(TAG, "Data Reset")
    }
}

/**
 * Factory function to create the Android analytics implementation.
 */
actual fun createAnalytics(): Analytics = AndroidAnalytics()

