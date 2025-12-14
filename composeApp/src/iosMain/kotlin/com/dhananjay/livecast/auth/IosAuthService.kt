
package com.dhananjay.livecast.auth

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Foundation.NSUUID

/**
 * iOS implementation of AuthService.
 * Uses local in-memory storage for now.
 * Note: Firebase iOS SDK integration requires CocoaPods/SPM setup in iosApp.
 * This provides a working implementation until Firebase is properly configured.
 */
class IosAuthService : AuthService {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    override val currentUser: User?
        get() = (_authState.value as? AuthState.Authenticated)?.user

    override fun isLoggedIn(): Boolean = currentUser != null

    override suspend fun signInWithEmailAndPassword(email: String, password: String): AuthResult {
        _authState.value = AuthState.Loading
        delay(500)

        if (!email.contains("@")) {
            val error = "Invalid email format"
            _authState.value = AuthState.Error(error)
            return AuthResult.Error(error)
        }

        if (password.length < 6) {
            val error = "Password must be at least 6 characters"
            _authState.value = AuthState.Error(error)
            return AuthResult.Error(error)
        }

        val user = User(
            id = generateUserId(email),
            email = email,
            displayName = email.substringBefore("@"),
            isAnonymous = false
        )

        _authState.value = AuthState.Authenticated(user)
        return AuthResult.Success(user)
    }

    override suspend fun createUserWithEmailAndPassword(email: String, password: String): AuthResult {
        return signInWithEmailAndPassword(email, password)
    }

    override suspend fun signInAnonymously(): AuthResult {
        _authState.value = AuthState.Loading
        delay(300)

        val user = User(
            id = NSUUID().UUIDString,
            email = null,
            displayName = "Guest",
            isAnonymous = true
        )

        _authState.value = AuthState.Authenticated(user)
        return AuthResult.Success(user)
    }

    override suspend fun signOut() {
        _authState.value = AuthState.Unauthenticated
    }

    override suspend fun sendPasswordResetEmail(email: String): AuthResult {
        delay(300)
        return AuthResult.Success(User(id = "", email = email, displayName = null))
    }

    private fun generateUserId(email: String): String {
        return "ios_${email.hashCode()}_${NSUUID().UUIDString.take(8)}"
    }
}
