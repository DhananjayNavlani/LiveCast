
package com.dhananjay.livecast.platform

/**
 * Desktop/JVM implementation of PlatformCapabilities.
 * Desktop can only subscribe (view broadcasts), not broadcast.
 */
class DesktopPlatformCapabilities : PlatformCapabilities {
    override val canBroadcast: Boolean = false
    override val platformName: String = "Desktop"
}

actual fun getPlatformCapabilities(): PlatformCapabilities = DesktopPlatformCapabilities()
