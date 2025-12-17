package com.dhananjay.livecast.cast.data.model

import android.os.Build
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import kotlinx.serialization.Serializable

/**
 * Device information for online user tracking.
 * This is stored as a nested object in the user document.
 */
@Serializable
@IgnoreExtraProperties
data class DeviceInfo(
    @PropertyName("device_id")
    var deviceId: String = "",

    @PropertyName("device_name")
    var deviceName: String = "",

    @PropertyName("manufacturer")
    var manufacturer: String = "",

    @PropertyName("model")
    var model: String = "",

    @PropertyName("brand")
    var brand: String = "",

    @PropertyName("sdk_version")
    var sdkVersion: Int = 0,

    @PropertyName("android_version")
    var androidVersion: String = "",

    @PropertyName("screen_width")
    var screenWidth: Int = 0,

    @PropertyName("screen_height")
    var screenHeight: Int = 0,

    @PropertyName("screen_density")
    var screenDensity: Float = 0f,

    @PropertyName("battery_level")
    var batteryLevel: Int = 0,

    @PropertyName("is_charging")
    var isCharging: Boolean = false,

    @PropertyName("network_type")
    var networkType: String = "",  // "wifi", "mobile", "ethernet", "none"

    @PropertyName("ip_address")
    var ipAddress: String = "",

    @PropertyName("app_version")
    var appVersion: String = "",

    @PropertyName("app_version_code")
    var appVersionCode: Int = 0,
) {
    companion object {
        /**
         * Create DeviceInfo from current device
         */
        fun fromCurrentDevice(
            screenWidth: Int = 0,
            screenHeight: Int = 0,
            screenDensity: Float = 0f,
            batteryLevel: Int = 0,
            isCharging: Boolean = false,
            networkType: String = "",
            ipAddress: String = "",
            appVersion: String = "",
            appVersionCode: Int = 0,
        ): DeviceInfo {
            return DeviceInfo(
                deviceId = "${Build.FINGERPRINT}_${Build.DEVICE}_${Build.MANUFACTURER}".hashCode().toString(),
                deviceName = "${Build.MANUFACTURER} ${Build.MODEL}",
                manufacturer = Build.MANUFACTURER,
                model = Build.MODEL,
                brand = Build.BRAND,
                sdkVersion = Build.VERSION.SDK_INT,
                androidVersion = Build.VERSION.RELEASE,
                screenWidth = screenWidth,
                screenHeight = screenHeight,
                screenDensity = screenDensity,
                batteryLevel = batteryLevel,
                isCharging = isCharging,
                networkType = networkType,
                ipAddress = ipAddress,
                appVersion = appVersion,
                appVersionCode = appVersionCode,
            )
        }
    }
}

