package com.dhananjay.livecast.auth

import android.app.Activity
import android.content.Intent
import com.firebase.ui.auth.AuthUI
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/**
 * Android implementation of AuthService using Firebase Authentication with Auth UI support.
 */
class AndroidFirebaseAuthService(
    private val firebaseAuth: FirebaseAuth
) : AuthService {

    private val _authState = MutableStateFlow<AuthState>(
        if (firebaseAuth.currentUser != null) {
            AuthState.Authenticated(firebaseAuth.currentUser!!.toUser())
        } else {
            AuthState.Unauthenticated
        }
    )

    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    override val currentUser: User?
        get() = firebaseAuth.currentUser?.toUser()

    init {
        firebaseAuth.addAuthStateListener { auth ->
            _authState.value = if (auth.currentUser != null) {
                AuthState.Authenticated(auth.currentUser!!.toUser())
            } else {
                AuthState.Unauthenticated
            }
        }
    }

    override fun isLoggedIn(): Boolean = firebaseAuth.currentUser != null

    override suspend fun signInWithEmailAndPassword(email: String, password: String): AuthResult {
        return try {
            _authState.value = AuthState.Loading
            val result = firebaseAuth.signInWithEmailAndPassword(email, password).await()
            result.user?.let { user ->
                AuthResult.Success(user.toUser())
            } ?: AuthResult.Error("Sign in failed: No user returned")
        } catch (e: Exception) {
            _authState.value = AuthState.Error(e.message ?: "Sign in failed")
            AuthResult.Error(e.message ?: "Sign in failed", e)
        }
    }

    override suspend fun createUserWithEmailAndPassword(email: String, password: String): AuthResult {
        return try {
            _authState.value = AuthState.Loading
            val result = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
            result.user?.let { user ->
                AuthResult.Success(user.toUser())
            } ?: AuthResult.Error("Account creation failed: No user returned")
        } catch (e: Exception) {
            _authState.value = AuthState.Error(e.message ?: "Account creation failed")
            AuthResult.Error(e.message ?: "Account creation failed", e)
        }
    }

    override suspend fun signInAnonymously(): AuthResult {
        return try {
            _authState.value = AuthState.Loading
            val result = firebaseAuth.signInAnonymously().await()
            result.user?.let { user ->
                AuthResult.Success(user.toUser())
            } ?: AuthResult.Error("Anonymous sign in failed: No user returned")
        } catch (e: Exception) {
            _authState.value = AuthState.Error(e.message ?: "Anonymous sign in failed")
            AuthResult.Error(e.message ?: "Anonymous sign in failed", e)
        }
    }

    override suspend fun signOut() {
        firebaseAuth.signOut()
        _authState.value = AuthState.Unauthenticated
    }

    override suspend fun sendPasswordResetEmail(email: String): AuthResult {
        return try {
            firebaseAuth.sendPasswordResetEmail(email).await()
            AuthResult.Success(User(id = "", email = email, displayName = null))
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Failed to send password reset email", e)
        }
    }

    /**
     * Creates an Intent to launch Firebase Auth UI for sign in.
     * Supports Email, Google, and Anonymous sign-in providers.
     */
    fun createSignInIntent(): Intent {
        val providers = listOf(
            AuthUI.IdpConfig.EmailBuilder().build(),
            AuthUI.IdpConfig.GoogleBuilder().build(),
            AuthUI.IdpConfig.AnonymousBuilder().build()
        )

        return AuthUI.getInstance()
            .createSignInIntentBuilder()
            .setAvailableProviders(providers)
            .build()
    }

    /**
     * Handle the result from Firebase Auth UI.
     * Call this in onActivityResult or from ActivityResultLauncher.
     */
    fun handleSignInResult(resultCode: Int): AuthResult {
        return if (resultCode == Activity.RESULT_OK) {
            firebaseAuth.currentUser?.let { user ->
                AuthResult.Success(user.toUser())
            } ?: AuthResult.Error("Sign in failed: No user returned")
        } else {
            AuthResult.Error("Sign in cancelled or failed")
        }
    }

    private fun FirebaseUser.toUser(): User = User(
        id = uid,
        email = email,
        displayName = displayName,
        photoUrl = photoUrl?.toString(),
        isAnonymous = isAnonymous
    )
}

