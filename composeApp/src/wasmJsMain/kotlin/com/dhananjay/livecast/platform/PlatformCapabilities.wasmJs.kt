package com.dhananjay.livecast.platform

/**
 * Web/WasmJs implementation of PlatformCapabilities.
 * Web can only subscribe (view broadcasts), not broadcast.
 */
class WebPlatformCapabilities : PlatformCapabilities {
    override val canBroadcast: Boolean = false
    override val platformName: String = "Web"
}

actual fun getPlatformCapabilities(): PlatformCapabilities = WebPlatformCapabilities()
