package com.dhananjay.livecast.platform

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import java.util.UUID

/**
 * Android implementation of DeviceInfo.
 */

private var appContext: Context? = null
private var cachedDeviceId: String? = null

/**
 * Initialize the device info system with application context.
 * Call this in Application.onCreate()
 */
fun initDeviceInfo(context: Context) {
    appContext = context.applicationContext
}

@SuppressLint("HardwareIds")
actual fun getDeviceInfo(): DeviceInfo {
    val context = appContext ?: throw IllegalStateException("DeviceInfo not initialized. Call initDeviceInfo(context) first.")

    val deviceId = getOrCreatePersistentDeviceId()

    return DeviceInfo(
        deviceId = deviceId,
        deviceName = "${Build.MANUFACTURER} ${Build.MODEL}",
        platform = "Android",
        osVersion = Build.VERSION.RELEASE,
        manufacturer = Build.MANUFACTURER,
        model = Build.MODEL,
        appVersion = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    )
}

@SuppressLint("HardwareIds")
actual fun getOrCreatePersistentDeviceId(): String {
    // Return cached value if available
    cachedDeviceId?.let { return it }

    val context = appContext ?: throw IllegalStateException("DeviceInfo not initialized. Call initDeviceInfo(context) first.")

    val prefs = context.getSharedPreferences("livecast_device", Context.MODE_PRIVATE)
    var deviceId = prefs.getString("device_id", null)

    if (deviceId == null) {
        // Generate a stable device ID combining multiple sources
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        val fingerprint = "${Build.FINGERPRINT}_${Build.DEVICE}_${Build.MANUFACTURER}"

        // Combine Android ID with device fingerprint for a unique but stable ID
        deviceId = if (androidId != null && androidId != "9774d56d682e549c") {
            // Use Android ID if available and not the known emulator ID
            "${androidId}_${fingerprint.hashCode().toUInt().toString(16)}"
        } else {
            // Fallback to generated UUID + device fingerprint
            "${UUID.randomUUID()}_${fingerprint.hashCode().toUInt().toString(16)}"
        }

        prefs.edit().putString("device_id", deviceId).apply()
    }

    cachedDeviceId = deviceId
    return deviceId
}

