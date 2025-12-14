package com.dhananjay.livecast.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.browser.localStorage
import kotlinx.coroutines.await
import kotlin.js.Promise

/**
 * WasmJS (Web) implementation of AuthService using Firebase JS SDK.
 * Requires Firebase configuration to be set via initializeFirebase() before use.
 */
class WasmAuthService : AuthService {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private var firebaseAuth: dynamic = null
    private var isInitialized = false

    override val currentUser: User?
        get() = (_authState.value as? AuthState.Authenticated)?.user

    /**
     * Initialize Firebase with configuration.
     * Call this before using any auth methods.
     * 
     * @param apiKey Firebase API key from Firebase Console
     * @param authDomain Firebase auth domain (projectId.firebaseapp.com)
     * @param projectId Firebase project ID
     * @param storageBucket Firebase storage bucket
     * @param messagingSenderId Firebase messaging sender ID
     * @param appId Firebase app ID
     */
    fun initializeFirebase(
        apiKey: String,
        authDomain: String,
        projectId: String,
        storageBucket: String,
        messagingSenderId: String,
        appId: String
    ) {
        if (!isInitialized) {
            try {
                val config = createFirebaseConfig(
                    apiKey = apiKey,
                    authDomain = authDomain,
                    projectId = projectId,
                    storageBucket = storageBucket,
                    messagingSenderId = messagingSenderId,
                    appId = appId
                )
                
                val app = FirebaseApp.initializeApp(config)
                firebaseAuth = FirebaseAuth.getAuth(app)
                
                // Listen for auth state changes
                FirebaseAuth.onAuthStateChanged(firebaseAuth) { user ->
                    _authState.value = if (user != null) {
                        AuthState.Authenticated(user.toUser())
                    } else {
                        AuthState.Unauthenticated
                    }
                }
                
                isInitialized = true
                
                // Check initial auth state
                val currentUserJs = firebaseAuth.currentUser
                if (currentUserJs != null) {
                    _authState.value = AuthState.Authenticated(currentUserJs.toUser())
                }
            } catch (e: Exception) {
                console.error("Firebase initialization failed", e)
                _authState.value = AuthState.Error("Firebase initialization failed: ${e.message}")
            }
        }
    }

    /**
     * Initialize Firebase from localStorage configuration.
     * Looks for firebase_config_* keys in localStorage.
     */
    fun initializeFromLocalStorage() {
        try {
            val apiKey = localStorage.getItem("firebase_config_api_key")
            val authDomain = localStorage.getItem("firebase_config_auth_domain")
            val projectId = localStorage.getItem("firebase_config_project_id")
            val storageBucket = localStorage.getItem("firebase_config_storage_bucket")
            val messagingSenderId = localStorage.getItem("firebase_config_messaging_sender_id")
            val appId = localStorage.getItem("firebase_config_app_id")

            if (apiKey != null && authDomain != null && projectId != null && 
                storageBucket != null && messagingSenderId != null && appId != null) {
                initializeFirebase(apiKey, authDomain, projectId, storageBucket, messagingSenderId, appId)
            } else {
                console.warn("Firebase configuration not found in localStorage")
            }
        } catch (e: Exception) {
            console.error("Failed to initialize Firebase from localStorage", e)
        }
    }

    init {
        // Try to initialize from localStorage on startup
        initializeFromLocalStorage()
    }

    override fun isLoggedIn(): Boolean = currentUser != null

    override suspend fun signInWithEmailAndPassword(email: String, password: String): AuthResult {
        if (!isInitialized) {
            return AuthResult.Error("Firebase not initialized. Call initializeFirebase() first or set firebase_config_* in localStorage")
        }

        _authState.value = AuthState.Loading

        return try {
            val promise = FirebaseAuth.signInWithEmailAndPassword(firebaseAuth, email, password) as Promise<dynamic>
            val userCredential = promise.await()
            val user = userCredential.user.toUser()
            _authState.value = AuthState.Authenticated(user)
            AuthResult.Success(user)
        } catch (e: Exception) {
            val errorMessage = e.message ?: "Sign in failed"
            _authState.value = AuthState.Error(errorMessage)
            AuthResult.Error(errorMessage, e)
        }
    }

    override suspend fun createUserWithEmailAndPassword(email: String, password: String): AuthResult {
        if (!isInitialized) {
            return AuthResult.Error("Firebase not initialized. Call initializeFirebase() first or set firebase_config_* in localStorage")
        }

        _authState.value = AuthState.Loading

        return try {
            val promise = FirebaseAuth.createUserWithEmailAndPassword(firebaseAuth, email, password) as Promise<dynamic>
            val userCredential = promise.await()
            val user = userCredential.user.toUser()
            _authState.value = AuthState.Authenticated(user)
            AuthResult.Success(user)
        } catch (e: Exception) {
            val errorMessage = e.message ?: "Account creation failed"
            _authState.value = AuthState.Error(errorMessage)
            AuthResult.Error(errorMessage, e)
        }
    }

    override suspend fun signInAnonymously(): AuthResult {
        if (!isInitialized) {
            return AuthResult.Error("Firebase not initialized. Call initializeFirebase() first or set firebase_config_* in localStorage")
        }

        _authState.value = AuthState.Loading

        return try {
            val promise = FirebaseAuth.signInAnonymously(firebaseAuth) as Promise<dynamic>
            val userCredential = promise.await()
            val user = userCredential.user.toUser()
            _authState.value = AuthState.Authenticated(user)
            AuthResult.Success(user)
        } catch (e: Exception) {
            val errorMessage = e.message ?: "Anonymous sign in failed"
            _authState.value = AuthState.Error(errorMessage)
            AuthResult.Error(errorMessage, e)
        }
    }

    override suspend fun signOut() {
        if (!isInitialized) {
            _authState.value = AuthState.Unauthenticated
            return
        }

        try {
            val promise = FirebaseAuth.signOut(firebaseAuth) as Promise<Unit>
            promise.await()
            _authState.value = AuthState.Unauthenticated
        } catch (e: Exception) {
            console.error("Sign out failed", e)
            _authState.value = AuthState.Unauthenticated
        }
    }

    override suspend fun sendPasswordResetEmail(email: String): AuthResult {
        if (!isInitialized) {
            return AuthResult.Error("Firebase not initialized. Call initializeFirebase() first or set firebase_config_* in localStorage")
        }

        return try {
            val promise = FirebaseAuth.sendPasswordResetEmail(firebaseAuth, email) as Promise<Unit>
            promise.await()
            AuthResult.Success(User(id = "", email = email, displayName = null))
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Failed to send password reset email", e)
        }
    }

    private fun dynamic.toUser(): User {
        return User(
            id = this.uid as String,
            email = this.email as? String,
            displayName = this.displayName as? String,
            photoUrl = this.photoURL as? String,
            isAnonymous = this.isAnonymous as? Boolean ?: false
        )
    }
}
