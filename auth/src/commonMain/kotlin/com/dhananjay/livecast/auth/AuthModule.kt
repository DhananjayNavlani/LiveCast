package com.dhananjay.livecast.auth

import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

/**
 * Koin module for cross-platform authentication.
 * Provides AuthService and LoginViewModel.
 */
val authModule = module {
    // AuthService is provided by platform-specific factory
    single<AuthService> { createAuthService() }

    // LoginViewModel uses the AuthService
    factoryOf(::LoginViewModel)
}
