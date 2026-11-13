
package com.dhananjay.livecast.crash

import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Android implementation using Firebase Crashlytics
 */
class AndroidCrashReporter : CrashReporter {
    private val crashlytics: FirebaseCrashlytics by lazy {
        FirebaseCrashlytics.getInstance()
    }

    override fun recordException(throwable: Throwable) {
        crashlytics.recordException(throwable)
    }

    override fun log(message: String) {
        crashlytics.log(message)
    }

    override fun setCustomKey(key: String, value: String) {
        crashlytics.setCustomKey(key, value)
    }

    override fun setUserId(userId: String) {
        crashlytics.setUserId(userId)
    }

    override fun setCrashlyticsCollectionEnabled(enabled: Boolean) {
        crashlytics.isCrashlyticsCollectionEnabled = enabled
    }
}

actual fun createCrashReporter(): CrashReporter = AndroidCrashReporter()
