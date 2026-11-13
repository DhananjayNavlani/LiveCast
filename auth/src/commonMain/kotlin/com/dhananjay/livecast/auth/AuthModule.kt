package com.dhananjay.livecast.auth

import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Koin module for cross-platform authentication.
 * Provides AuthService, SessionProvider, IdTokenProvider, and LoginViewModel.
 */
val authModule = module {
    // AuthService is provided by platform-specific factory
    val auth = single<AuthService> { createAuthService() }
    auth bind SessionProvider::class
    auth bind IdTokenProvider::class
    auth bind EmailVerifier::class

    // LoginViewModel uses the AuthService
    factoryOf(::LoginViewModel)
}
