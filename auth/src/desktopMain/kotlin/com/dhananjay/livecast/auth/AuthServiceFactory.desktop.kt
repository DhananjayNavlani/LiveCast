package com.dhananjay.livecast.auth

import java.io.File

actual fun createAuthService(): AuthService {
    val configDir = File(System.getProperty("user.home"), ".livecast")
    val apiKeyProvider = { DesktopAuthService.loadApiKeyFromConfig(configDir) }
    return DesktopAuthService(
        store = PropertiesAuthSessionStore(configDir),
        refresher = SecureTokenRefresher(apiKeyProvider),
        apiKeyProvider = apiKeyProvider,
        verificationSender = RestVerificationEmailSender(apiKeyProvider)
    )
}
