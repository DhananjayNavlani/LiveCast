package com.dhananjay.livecast.auth

/**
 * Google Sign-In request state for platform-specific handling.
 */
expect class GoogleSignInHandler {
    /**
     * Returns true if Google Sign-In is available on this platform.
     */
    fun isAvailable(): Boolean
}

/**
 * Factory function to create platform-specific Google Sign-In handler.
 */
expect fun createGoogleSignInHandler(): GoogleSignInHandler

