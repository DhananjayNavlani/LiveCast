package com.dhananjay.livecast.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * WasmJS (Web) implementation of AuthService.
 * This is a stub implementation for web that doesn't use Firebase directly.
 * Firebase integration for wasmJs requires Firebase JS SDK to be loaded via script tags
 * and proper JS interop which is not fully supported in Kotlin/Wasm yet.
 */
class WasmAuthService : AuthService {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    override val currentUser: User?
        get() = (_authState.value as? AuthState.Authenticated)?.user

    override fun isLoggedIn(): Boolean = currentUser != null

    override suspend fun signInWithEmailAndPassword(email: String, password: String): AuthResult {
        // Stub implementation - web auth not yet supported in Wasm
        println("[WasmAuth] signInWithEmailAndPassword called - not implemented for Wasm")
        return AuthResult.Error("Authentication is not yet supported on web platform")
    }

    override suspend fun createUserWithEmailAndPassword(email: String, password: String): AuthResult {
        println("[WasmAuth] createUserWithEmailAndPassword called - not implemented for Wasm")
        return AuthResult.Error("Authentication is not yet supported on web platform")
    }

    override suspend fun signInAnonymously(): AuthResult {
        println("[WasmAuth] signInAnonymously called - not implemented for Wasm")
        return AuthResult.Error("Authentication is not yet supported on web platform")
    }

    override suspend fun signOut() {
        println("[WasmAuth] signOut called")
        _authState.value = AuthState.Unauthenticated
    }

    override suspend fun sendPasswordResetEmail(email: String): AuthResult {
        println("[WasmAuth] sendPasswordResetEmail called - not implemented for Wasm")
        return AuthResult.Error("Password reset is not yet supported on web platform")
    }
}
