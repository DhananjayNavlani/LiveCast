
package com.dhananjay.livecast.platform

/**
 * Platform capabilities interface for determining what features
 * are available on each platform.
 * 
 * - Android: Can be both Broadcaster and Subscriber
 * - iOS, Desktop, Web: Can only be Subscriber (viewer)
 */
interface PlatformCapabilities {
    /**
     * Returns true if the platform supports broadcasting (screen sharing).
     * Only Android supports this feature due to AccessibilityService requirements.
     */
    val canBroadcast: Boolean
    
    /**
     * Returns true if the platform supports subscribing (viewing broadcasts).
     * All platforms support this.
     */
    val canSubscribe: Boolean get() = true
    
    /**
     * Platform name for display purposes
     */
    val platformName: String
}

/**
 * Factory function to get platform-specific capabilities instance
 */
expect fun getPlatformCapabilities(): PlatformCapabilities
