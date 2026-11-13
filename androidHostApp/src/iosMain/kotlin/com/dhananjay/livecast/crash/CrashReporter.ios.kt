
package com.dhananjay.livecast.crash

/**
 * iOS implementation that logs to console.
 * Note: To use Firebase Crashlytics on iOS, you need to:
 * 1. Add Firebase SDK via CocoaPods or SPM in the iosApp
 * 2. Initialize Firebase in the iOS app's AppDelegate
 * 3. Use cocoapods {} block in build.gradle.kts to expose Crashlytics to Kotlin
 * 
 * For now, this provides a console-based fallback implementation.
 * In a production app, you would integrate with Firebase iOS SDK.
 */
class IosCrashReporter : CrashReporter {
    private val customKeys = mutableMapOf<String, String>()
    private var userId: String? = null
    private var collectionEnabled = true

    override fun recordException(throwable: Throwable) {
        if (!collectionEnabled) return

        val logMessage = buildString {
            appendLine("=== CRASH REPORT (iOS) ===")
            userId?.let { appendLine("User ID: $it") }
            if (customKeys.isNotEmpty()) {
                appendLine("Custom Keys: $customKeys")
            }
            appendLine("Exception: ${throwable::class.simpleName}")
            appendLine("Message: ${throwable.message}")
            appendLine("Stack Trace:")
            appendLine(throwable.stackTraceToString())
            appendLine("==========================")
        }

        println(logMessage)
    }

    override fun log(message: String) {
        if (!collectionEnabled) return
        println("[CrashReporter] $message")
    }

    override fun setCustomKey(key: String, value: String) {
        customKeys[key] = value
    }

    override fun setUserId(userId: String) {
        this.userId = userId
    }

    override fun setCrashlyticsCollectionEnabled(enabled: Boolean) {
        collectionEnabled = enabled
    }
}

actual fun createCrashReporter(): CrashReporter = IosCrashReporter()
