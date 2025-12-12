package com.dhananjay.livecast.auth

actual fun createAuthService(): AuthService {
    return IosAuthService()
}
