package com.dhananjay.livecast.platform

import kotlin.random.Random

/**
 * WasmJS (Web) implementation of DeviceInfo.
 * Uses browser localStorage for persistence and browser fingerprinting for device identification.
 */

private var cachedDeviceId: String? = null

actual fun getDeviceInfo(): DeviceInfo {
    return DeviceInfo(
        deviceId = getOrCreatePersistentDeviceId(),
        deviceName = getBrowserInfo(),
        platform = "Web",
        osVersion = "Browser",
        manufacturer = "Browser",
        model = "WebApp"
    )
}

actual fun getOrCreatePersistentDeviceId(): String {
    // Return cached value if available
    cachedDeviceId?.let { return it }

    var deviceId = getFromLocalStorage("livecast_device_id")

    if (deviceId == null) {
        // Generate a browser fingerprint combined with random UUID
        val fingerprint = generateBrowserFingerprint()
        val randomPart = generateRandomId()
        deviceId = "${fingerprint}_$randomPart"

        saveToLocalStorage("livecast_device_id", deviceId)
    }

    cachedDeviceId = deviceId
    return deviceId
}

private fun getBrowserInfo(): String {
    return try {
        js("navigator.userAgent").toString()
    } catch (e: Exception) {
        "Unknown Browser"
    }
}

private fun generateBrowserFingerprint(): String {
    return try {
        val userAgent = js("navigator.userAgent").toString()
        val language = js("navigator.language").toString()
        val platform = js("navigator.platform").toString()
        val screenInfo = "${js("screen.width")}x${js("screen.height")}"

        val combined = "$userAgent$language$platform$screenInfo"
        combined.hashCode().toUInt().toString(16)
    } catch (e: Exception) {
        Random.nextInt().toUInt().toString(16)
    }
}

private fun generateRandomId(): String {
    return Random.nextLong().toULong().toString(16) + Random.nextLong().toULong().toString(16)
}

private fun getFromLocalStorage(key: String): String? {
    return try {
        val value = js("localStorage.getItem(key)")
        if (value == null || js("value === null || value === undefined").toString() == "true") {
            null
        } else {
            value.toString()
        }
    } catch (e: Exception) {
        null
    }
}

private fun saveToLocalStorage(key: String, value: String) {
    try {
        js("localStorage.setItem(key, value)")
    } catch (e: Exception) {
        // localStorage might not be available in some contexts
    }
}

