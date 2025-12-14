package com.dhananjay.livecast.analytics

/**
 * Desktop (JVM) implementation that prints to console.
 */
class DesktopAnalytics : Analytics {
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
        println(logMessage)
    }

    override fun logScreenView(screenName: String, screenClass: String?) {
        if (!collectionEnabled) return

        val logMessage = buildString {
            append("[Analytics] Screen View: $screenName")
            screenClass?.let { append(" | Class: $it") }
        }
        println(logMessage)
    }

    override fun setUserProperty(name: String, value: String?) {
        userProperties[name] = value
        println("[Analytics] User Property Set: $name = $value")
    }

    override fun setUserId(userId: String?) {
        this.userId = userId
        println("[Analytics] User ID Set: $userId")
    }

    override fun setAnalyticsCollectionEnabled(enabled: Boolean) {
        collectionEnabled = enabled
        println("[Analytics] Collection Enabled: $enabled")
    }

    override fun resetAnalyticsData() {
        userProperties.clear()
        userId = null
        println("[Analytics] Data Reset")
    }
}

/**
 * Factory function to create the Desktop analytics implementation.
 */
actual fun createAnalytics(): Analytics = DesktopAnalytics()

