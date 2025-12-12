package com.dhananjay.livecast.auth

actual fun createAuthService(): AuthService {
    return WasmAuthService()
}
