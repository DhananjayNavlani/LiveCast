package com.dhananjay.livecast.auth

import kotlinx.coroutines.flow.StateFlow

/**
 * Represents the current authentication state
 */
sealed class AuthState {
    /** User is not authenticated */
    data object Unauthenticated : AuthState()

    /** Authentication is in progress */
    data object Loading : AuthState()

    /** User is authenticated */
    data class Authenticated(val user: User) : AuthState()

    /** Authentication failed */
    data class Error(val message: String) : AuthState()
}

/**
 * Represents a logged-in user
 */
data class User(
    val id: String,
    val email: String?,
    val displayName: String?,
    val photoUrl: String? = null,
    val isAnonymous: Boolean = false,
    val deviceId: String? = null
)

/**
 * Result of an authentication operation
 */
sealed class AuthResult {
    data class Success(val user: User) : AuthResult()
    data class Error(val message: String, val exception: Throwable? = null) : AuthResult()
}

/**
 * Cross-platform authentication service interface.
 * Platform-specific implementations will handle actual authentication.
 */
interface AuthService {
    /**
     * Current authentication state as a Flow
     */
    val authState: StateFlow<AuthState>

    /**
     * Get the currently logged in user, or null if not authenticated
     */
    val currentUser: User?

    /**
     * Check if user is currently logged in
     */
    fun isLoggedIn(): Boolean

    /**
     * Sign in with email and password
     */
    suspend fun signInWithEmailAndPassword(email: String, password: String): AuthResult

    /**
     * Create a new account with email and password
     */
    suspend fun createUserWithEmailAndPassword(email: String, password: String): AuthResult

    /**
     * Sign in anonymously (guest mode)
     */
    suspend fun signInAnonymously(): AuthResult

    /**
     * Sign out the current user
     */
    suspend fun signOut()

    /**
     * Send password reset email
     */
    suspend fun sendPasswordResetEmail(email: String): AuthResult

    /**
     * Sign in with Google using ID token
     */
    suspend fun signInWithGoogle(idToken: String): AuthResult
}

