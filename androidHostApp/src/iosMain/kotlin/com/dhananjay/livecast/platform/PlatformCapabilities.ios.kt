
package com.dhananjay.livecast.platform

/**
 * iOS implementation of PlatformCapabilities.
 * iOS can only subscribe (view broadcasts), not broadcast.
 */
class IOSPlatformCapabilities : PlatformCapabilities {
    override val platformName: String = "iOS"
}

actual fun getPlatformCapabilities(): PlatformCapabilities = IOSPlatformCapabilities()
