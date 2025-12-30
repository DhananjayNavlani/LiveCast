package com.dhananjay.livecast.platform

import java.io.File
import java.util.Properties
import java.util.UUID

/**
 * Desktop (JVM) implementation of DeviceInfo.
 */

private var cachedDeviceId: String? = null

actual fun getDeviceInfo(): DeviceInfo {
    return DeviceInfo(
        deviceId = getOrCreatePersistentDeviceId(),
        deviceName = "${System.getProperty("os.name")} - ${System.getProperty("user.name")}",
        platform = "Desktop",
        osVersion = System.getProperty("os.version") ?: "unknown",
        manufacturer = System.getProperty("os.name") ?: "unknown",
        model = System.getProperty("os.arch") ?: "unknown"
    )
}

actual fun getOrCreatePersistentDeviceId(): String {
    // Return cached value if available
    cachedDeviceId?.let { return it }

    val configDir = File(System.getProperty("user.home"), ".livecast")
    val deviceFile = File(configDir, "device.properties")

    if (!configDir.exists()) {
        configDir.mkdirs()
    }

    var deviceId: String? = null

    if (deviceFile.exists()) {
        try {
            val props = Properties().apply {
                deviceFile.inputStream().use { load(it) }
            }
            deviceId = props.getProperty("device_id")
        } catch (e: Exception) {
            // Ignore and generate new ID
        }
    }

    if (deviceId == null) {
        // Generate a unique device ID combining hardware info with UUID
        val osInfo = "${System.getProperty("os.name")}_${System.getProperty("os.arch")}_${System.getProperty("user.name")}"
        val uuid = UUID.randomUUID().toString()
        deviceId = "${osInfo.hashCode().toUInt().toString(16)}_$uuid"

        try {
            val props = Properties().apply {
                setProperty("device_id", deviceId)
            }
            deviceFile.outputStream().use { props.store(it, "LiveCast Device ID") }
        } catch (e: Exception) {
            // Failed to save, but continue with the generated ID
        }
    }

    cachedDeviceId = deviceId
    return deviceId
}

