
package com.dhananjay.livecast.platform

/**
 * Android implementation of PlatformCapabilities.
 * Android can both broadcast (via AccessibilityService + MediaProjection) and subscribe.
 */
class AndroidPlatformCapabilities : PlatformCapabilities {
    override val canBroadcast: Boolean = true
    override val platformName: String = "Android"
}

actual fun getPlatformCapabilities(): PlatformCapabilities = AndroidPlatformCapabilities()
