package com.dhananjay.livecast.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * iOS stub implementation of AuthService.
 *
 * TODO: M1 will reimplement via Firebase in the iosApp Xcode target exposed through an expect/actual interface.
 * swiftklib was removed due to Xcode 27.2 incompatibility.
 */
class IosAuthService : AuthService {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    override val currentUser: User? = null

    override fun isLoggedIn(): Boolean = false

    override suspend fun signInWithEmailAndPassword(email: String, password: String): AuthResult {
        return AuthResult.Error("iOS Firebase auth not yet implemented (swiftklib removed; see M1 Swift bridge)")
    }

    override suspend fun createUserWithEmailAndPassword(email: String, password: String): AuthResult {
        return AuthResult.Error("iOS Firebase auth not yet implemented (swiftklib removed; see M1 Swift bridge)")
    }

    override suspend fun signInAnonymously(): AuthResult {
        return AuthResult.Error("iOS Firebase auth not yet implemented (swiftklib removed; see M1 Swift bridge)")
    }

    override suspend fun signOut() {
        _authState.value = AuthState.Unauthenticated
    }

    override suspend fun sendPasswordResetEmail(email: String): AuthResult {
        return AuthResult.Error("iOS Firebase auth not yet implemented (swiftklib removed; see M1 Swift bridge)")
    }

    override suspend fun getIdToken(forceRefresh: Boolean): String? = null
}
