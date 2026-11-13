package com.dhananjay.livecast.pairing

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.dhananjay.livecast.protocol.HostDevice

interface HostDeviceInfoProvider {
    fun current(deviceId: String): HostDevice
}

class AndroidHostDeviceInfoProvider(
    private val context: Context
) : HostDeviceInfoProvider {
    override fun current(deviceId: String): HostDevice {
        val rawName = runCatching {
            Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
        }.getOrNull()
        val displayName = if (!rawName.isNullOrBlank()) rawName else Build.MODEL
        return HostDevice(
            id = deviceId,
            displayName = displayName,
            model = Build.MODEL
        )
    }
}
