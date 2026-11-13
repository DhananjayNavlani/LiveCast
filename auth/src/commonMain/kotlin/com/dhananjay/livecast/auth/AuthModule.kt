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
    single<AuthService> { createAuthService() } bind SessionProvider::class bind IdTokenProvider::class

    // LoginViewModel uses the AuthService
    factoryOf(::LoginViewModel)
}
