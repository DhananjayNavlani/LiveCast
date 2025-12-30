
package com.dhananjay.livecast.auth

import com.dhananjay.livecast.auth.ios.FirebaseAuthBridge
import com.dhananjay.livecast.auth.ios.FirebaseUserData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * iOS implementation of AuthService using Firebase iOS SDK.
 * Uses swiftklib to call Firebase Auth natively through Swift.
 */
class IosAuthService : AuthService {

    private val firebaseAuthBridge = FirebaseAuthBridge.shared

    private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        // Configure Firebase (should already be configured in App Delegate, but ensure it's done)
        firebaseAuthBridge.configure()

        // Set initial state based on current user
        updateAuthStateFromCurrentUser()

        // Listen for auth state changes
        firebaseAuthBridge.addAuthStateListener { userData ->
            if (userData != null) {
                _authState.value = AuthState.Authenticated(userData.toUser())
            } else {
                _authState.value = AuthState.Unauthenticated
            }
        }
    }

    private fun updateAuthStateFromCurrentUser() {
        val currentUser = firebaseAuthBridge.getCurrentUser()
        _authState.value = if (currentUser != null) {
            AuthState.Authenticated(currentUser.toUser())
        } else {
            AuthState.Unauthenticated
        }
    }

    override val currentUser: User?
        get() = firebaseAuthBridge.getCurrentUser()?.toUser()

    override fun isLoggedIn(): Boolean = firebaseAuthBridge.isSignedIn()

    override suspend fun signInWithEmailAndPassword(email: String, password: String): AuthResult {
        _authState.value = AuthState.Loading
        
        return suspendCancellableCoroutine { continuation ->
            firebaseAuthBridge.signInWithEmail(email, password = password) { result ->
                val authResult = handleAuthResult(result)
                continuation.resume(authResult)
            }
        }
    }

    override suspend fun createUserWithEmailAndPassword(email: String, password: String): AuthResult {
        _authState.value = AuthState.Loading
        
        return suspendCancellableCoroutine { continuation ->
            firebaseAuthBridge.createUserWithEmail(email, password = password) { result ->
                val authResult = handleAuthResult(result)
                continuation.resume(authResult)
            }
        }
    }

    override suspend fun signInAnonymously(): AuthResult {
        _authState.value = AuthState.Loading
        
        return suspendCancellableCoroutine { continuation ->
            firebaseAuthBridge.signInAnonymously { result ->
                val authResult = handleAuthResult(result)
                continuation.resume(authResult)
            }
        }
    }

    override suspend fun signOut() {
        val success = firebaseAuthBridge.signOut()
        if (success) {
            _authState.value = AuthState.Unauthenticated
        }
    }

    override suspend fun sendPasswordResetEmail(email: String): AuthResult {
        return suspendCancellableCoroutine { continuation ->
            firebaseAuthBridge.sendPasswordResetEmail(email) { result ->
                if (result.success && result.user != null) {
                    continuation.resume(AuthResult.Success(result.user!!.toUser()))
                } else {
                    val errorMessage = result.errorMessage ?: "Unknown error occurred"
                    continuation.resume(AuthResult.Error(errorMessage, Exception(errorMessage)))
                }
            }
        }
    }

    override suspend fun signInWithGoogle(idToken: String): AuthResult {
        _authState.value = AuthState.Loading

        return suspendCancellableCoroutine { continuation ->
            firebaseAuthBridge.signInWithGoogleIdToken(idToken) { result ->
                val authResult = handleAuthResult(result)
                continuation.resume(authResult)
            }
        }
    }

    private fun handleAuthResult(result: com.dhananjay.livecast.auth.ios.FirebaseAuthResultData): AuthResult {
        return if (result.success && result.user != null) {
            val user = result.user!!.toUser()
            _authState.value = AuthState.Authenticated(user)
            AuthResult.Success(user)
        } else {
            val errorMessage = result.errorMessage ?: "Unknown error occurred"
            _authState.value = AuthState.Error(errorMessage)
            AuthResult.Error(errorMessage, Exception(errorMessage))
        }
    }

    private fun FirebaseUserData.toUser(): User {
        return User(
            id = uid,
            email = email,
            displayName = displayName,
            photoUrl = photoURL,
            isAnonymous = isAnonymous,
            deviceId = getOrCreatePersistentDeviceId()
        )
    }
}
