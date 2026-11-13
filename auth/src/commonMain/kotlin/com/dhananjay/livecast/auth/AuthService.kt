package com.dhananjay.livecast.auth

import kotlinx.coroutines.flow.Flow
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
    val emailVerified: Boolean = false
)

/**
 * Result of an authentication operation
 */
sealed class AuthResult {
    data class Success(val user: User) : AuthResult()
    data class Error(val message: String, val exception: Throwable? = null) : AuthResult()
}

/**
 * Provider interface for obtaining the current user's ID token.
 */
interface IdTokenProvider {
    suspend fun getIdToken(forceRefresh: Boolean = false): String?
}

/**
 * Provider interface for observing authentication state and the current user.
 */
interface SessionProvider : IdTokenProvider {
    val authState: StateFlow<AuthState>
    val currentUser: User?
}

/**
 * Interface for email verification operations.
 */
interface EmailVerifier {
    /**
     * Sends a verification email to the current user's email address.
     */
    suspend fun sendVerificationEmail(): AuthResult

    /**
     * Reloads the account AND force-refreshes the ID token (rules read the token claim),
     * re-emits authState with the updated user, returns emailVerified.
     */
    suspend fun refreshEmailVerification(): Result<Boolean>
}

/**
 * Cross-platform authentication service interface.
 * Platform-specific implementations will handle actual authentication.
 */
interface AuthService : SessionProvider, EmailVerifier {
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
}
