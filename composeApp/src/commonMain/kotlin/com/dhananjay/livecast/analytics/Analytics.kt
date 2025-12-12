package com.dhananjay.livecast.analytics

/**
 * Multiplatform analytics interface.
 * Uses Firebase Analytics on Android/iOS, and logs to console on Desktop/Web.
 */
interface Analytics {
    /**
     * Log an event with optional parameters
     */
    fun logEvent(eventName: String, params: Map<String, Any>? = null)

    /**
     * Log a screen view event
     */
    fun logScreenView(screenName: String, screenClass: String? = null)

    /**
     * Set a user property
     */
    fun setUserProperty(name: String, value: String?)

    /**
     * Set the user ID for analytics
     */
    fun setUserId(userId: String?)

    /**
     * Enable or disable analytics collection
     */
    fun setAnalyticsCollectionEnabled(enabled: Boolean)

    /**
     * Reset analytics data
     */
    fun resetAnalyticsData()
}

/**
 * Common event names for consistent tracking across platforms
 */
object AnalyticsEvents {
    const val SCREEN_VIEW = "screen_view"
    const val LOGIN = "login"
    const val LOGOUT = "logout"
    const val SIGN_UP = "sign_up"
    const val SHARE = "share"
    const val SELECT_CONTENT = "select_content"
    const val BUTTON_CLICK = "button_click"
    const val APP_OPEN = "app_open"
    const val APP_BACKGROUND = "app_background"
}

/**
 * Common parameter names for consistent tracking across platforms
 */
object AnalyticsParams {
    const val SCREEN_NAME = "screen_name"
    const val SCREEN_CLASS = "screen_class"
    const val METHOD = "method"
    const val CONTENT_TYPE = "content_type"
    const val ITEM_ID = "item_id"
    const val BUTTON_NAME = "button_name"
}

/**
 * Factory function to get platform-specific Analytics instance
 */
expect fun createAnalytics(): Analytics

