package com.dhananjay.livecast.auth

/**
 * Factory function to create the platform-specific AuthService.
 * Each platform provides its own implementation.
 * - Android: Firebase Auth UI
 * - iOS: Firebase iOS SDK
 * - Desktop: Local auth (Firebase not supported)
 * - Web: Firebase JS SDK
 */
expect fun createAuthService(): AuthService

