package com.dhananjay.livecast.platform

import kotlinx.serialization.Serializable

/**
 * Cross-platform device information for unique device identification.
 * Used to track devices across different sign-in methods (email, Google, anonymous).
 */
@Serializable
data class DeviceInfo(
    val deviceId: String,
    val deviceName: String,
    val platform: String,
    val osVersion: String,
    val appVersion: String = "",
    val manufacturer: String = "",
    val model: String = ""
) {
    companion object {
        /**
         * Generate a unique device fingerprint that persists across app reinstalls.
         * This combines multiple device attributes to create a stable identifier.
         */
        fun generateFingerprint(vararg attributes: String): String {
            val combined = attributes.joinToString("_")
            return combined.hashCode().toUInt().toString(16)
        }
    }
}

/**
 * Factory function to get device info for the current platform.
 * Each platform provides its own implementation.
 */
expect fun getDeviceInfo(): DeviceInfo

/**
 * Get or create a persistent device ID.
 * This ID persists across app reinstalls using platform-specific storage.
 */
expect fun getOrCreatePersistentDeviceId(): String

