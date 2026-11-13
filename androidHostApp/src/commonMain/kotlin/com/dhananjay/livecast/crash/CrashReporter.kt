
package com.dhananjay.livecast.crash

/**
 * Multiplatform crash reporting interface.
 * Uses Firebase Crashlytics on Android/iOS, and logs to console on Desktop/Web.
 */
interface CrashReporter {
    /**
     * Record a non-fatal exception
     */
    fun recordException(throwable: Throwable)

    /**
     * Log a message for crash context
     */
    fun log(message: String)

    /**
     * Set a custom key-value pair for crash context
     */
    fun setCustomKey(key: String, value: String)

    /**
     * Set the user identifier
     */
    fun setUserId(userId: String)

    /**
     * Enable or disable crash collection
     */
    fun setCrashlyticsCollectionEnabled(enabled: Boolean)
}

/**
 * Factory function to get platform-specific CrashReporter instance
 */
expect fun createCrashReporter(): CrashReporter
