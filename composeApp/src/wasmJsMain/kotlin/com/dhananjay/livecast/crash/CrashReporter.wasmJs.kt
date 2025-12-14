package com.dhananjay.livecast.crash

/**
 * Web (WasmJs) implementation that logs to browser console.
 * In production, you could integrate with services like Sentry, LogRocket, etc.
 */
class WasmJsCrashReporter : CrashReporter {
    private val customKeys = mutableMapOf<String, String>()
    private var userId: String? = null
    private var collectionEnabled = true

    override fun recordException(throwable: Throwable) {
        if (!collectionEnabled) return

        val logMessage = buildString {
            appendLine("=== CRASH REPORT (Web) ===")
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

        // Log to browser console using println (maps to console.log in Wasm)
        println("[ERROR] $logMessage")
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

/**
 * Factory function to create the Web crash reporter implementation.
 */
actual fun createCrashReporter(): CrashReporter = WasmJsCrashReporter()
