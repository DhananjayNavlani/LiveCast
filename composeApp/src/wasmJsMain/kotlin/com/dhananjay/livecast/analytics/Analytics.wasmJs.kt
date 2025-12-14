
package com.dhananjay.livecast.analytics

/**
 * Web (WasmJS) implementation that logs to browser console.
 * In production, you could integrate with services like Google Analytics, Mixpanel, etc.
 */
class WasmJsAnalytics : Analytics {
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
        console.log(logMessage)
    }

    override fun logScreenView(screenName: String, screenClass: String?) {
        if (!collectionEnabled) return

        val logMessage = buildString {
            append("[Analytics] Screen View: $screenName")
            screenClass?.let { append(" | Class: $it") }
        }
        console.log(logMessage)
    }

    override fun setUserProperty(name: String, value: String?) {
        userProperties[name] = value
        console.log("[Analytics] User Property Set: $name = $value")
    }

    override fun setUserId(userId: String?) {
        this.userId = userId
        console.log("[Analytics] User ID Set: $userId")
    }

    override fun setAnalyticsCollectionEnabled(enabled: Boolean) {
        collectionEnabled = enabled
        console.log("[Analytics] Collection Enabled: $enabled")
    }

    override fun resetAnalyticsData() {
        userProperties.clear()
        userId = null
        console.log("[Analytics] Data Reset")
    }
}

actual fun createAnalytics(): Analytics = WasmJsAnalytics()

/**
 * External declaration for JavaScript console
 */
external object console {
    fun log(message: String)
}
