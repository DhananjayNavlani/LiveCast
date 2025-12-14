
package com.dhananjay.livecast.crash

import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Desktop (JVM) implementation that logs to console and optionally to a file.
 * In production, you could integrate with services like Sentry, Bugsnag, etc.
 */
class DesktopCrashReporter : CrashReporter {
    private val customKeys = mutableMapOf<String, String>()
    private var userId: String? = null
    private var collectionEnabled = true

    private val crashLogFile: File? by lazy {
        try {
            val userHome = System.getProperty("user.home")
            val logDir = File(userHome, ".livecast/logs")
            logDir.mkdirs()
            File(logDir, "crash.log")
        } catch (e: Exception) {
            null
        }
    }

    override fun recordException(throwable: Throwable) {
        if (!collectionEnabled) return

        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val stackTrace = sw.toString()

        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val logMessage = buildString {
            appendLine("=== CRASH REPORT ===")
            appendLine("Timestamp: $timestamp")
            userId?.let { appendLine("User ID: $it") }
            if (customKeys.isNotEmpty()) {
                appendLine("Custom Keys: $customKeys")
            }
            appendLine("Exception: ${throwable::class.simpleName}")
            appendLine("Message: ${throwable.message}")
            appendLine("Stack Trace:")
            appendLine(stackTrace)
            appendLine("====================")
        }

        // Log to console
        System.err.println(logMessage)

        // Log to file
        crashLogFile?.let { file ->
            try {
                file.appendText(logMessage + "\n")
            } catch (e: Exception) {
                System.err.println("Failed to write crash log to file: ${e.message}")
            }
        }
    }

    override fun log(message: String) {
        if (!collectionEnabled) return
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        println("[CrashReporter $timestamp] $message")
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

actual fun createCrashReporter(): CrashReporter = DesktopCrashReporter()
