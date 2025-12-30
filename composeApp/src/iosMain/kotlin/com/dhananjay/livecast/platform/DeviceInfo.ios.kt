package com.dhananjay.livecast.platform

import platform.UIKit.UIDevice
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUUID

/**
 * iOS implementation of DeviceInfo.
 */

private var cachedDeviceId: String? = null

actual fun getDeviceInfo(): DeviceInfo {
    val device = UIDevice.currentDevice

    return DeviceInfo(
        deviceId = getOrCreatePersistentDeviceId(),
        deviceName = device.name,
        platform = "iOS",
        osVersion = device.systemVersion,
        manufacturer = "Apple",
        model = device.model
    )
}

actual fun getOrCreatePersistentDeviceId(): String {
    // Return cached value if available
    cachedDeviceId?.let { return it }

    val defaults = NSUserDefaults.standardUserDefaults
    var deviceId = defaults.stringForKey("livecast_device_id")

    if (deviceId == null) {
        // Use vendor identifier combined with generated UUID for persistence
        val vendorId = UIDevice.currentDevice.identifierForVendor?.UUIDString ?: ""
        val uuid = NSUUID().UUIDString

        deviceId = if (vendorId.isNotEmpty()) {
            "${vendorId}_${uuid.hashCode().toUInt().toString(16)}"
        } else {
            uuid
        }

        defaults.setObject(deviceId, forKey = "livecast_device_id")
        defaults.synchronize()
    }

    cachedDeviceId = deviceId
    return deviceId
}

