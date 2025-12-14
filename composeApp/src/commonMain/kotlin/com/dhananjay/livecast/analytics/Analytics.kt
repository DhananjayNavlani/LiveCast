package com.dhananjay.livecast.analytics

/**
 * Analytics interface for tracking events and user properties across platforms.
 */
interface Analytics {
    /**
     * Logs an analytics event with optional parameters.
     *
     * @param eventName The name of the event to log
     * @param params Optional map of parameters associated with the event
     */
    fun logEvent(eventName: String, params: Map<String, Any>? = null)

    /**
     * Logs a screen view event.
     *
     * @param screenName The name of the screen being viewed
     * @param screenClass Optional class name of the screen
     */
    fun logScreenView(screenName: String, screenClass: String? = null)

    /**
     * Sets a user property.
     *
     * @param name The name of the user property
     * @param value The value of the user property
     */
    fun setUserProperty(name: String, value: String?)

    /**
     * Sets the user ID.
     *
     * @param userId The user ID to set
     */
    fun setUserId(userId: String?)

    /**
     * Enables or disables analytics collection.
     *
     * @param enabled Whether analytics collection should be enabled
     */
    fun setAnalyticsCollectionEnabled(enabled: Boolean)

    /**
     * Resets all analytics data.
     */
    fun resetAnalyticsData()
}

/**
 * Factory function to create a platform-specific Analytics instance.
 */
expect fun createAnalytics(): Analytics

