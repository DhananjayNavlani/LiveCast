package com.dhananjay.livecast.auth

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.browser.localStorage
import kotlin.random.Random

/**
 * WasmJS (Web) implementation of AuthService.
 * Uses browser localStorage for persistence.
 */
class WasmAuthService : AuthService {

    private val USER_ID_KEY = "livecast_user_id"
    private val USER_EMAIL_KEY = "livecast_user_email"
    private val USER_NAME_KEY = "livecast_user_name"
    private val IS_ANONYMOUS_KEY = "livecast_is_anonymous"

    private val _authState = MutableStateFlow<AuthState>(loadInitialState())
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    override val currentUser: User?
        get() = (_authState.value as? AuthState.Authenticated)?.user

    private fun loadInitialState(): AuthState {
        return try {
            val userId = localStorage.getItem(USER_ID_KEY)
            if (userId != null) {
                AuthState.Authenticated(
                    User(
                        id = userId,
                        email = localStorage.getItem(USER_EMAIL_KEY),
                        displayName = localStorage.getItem(USER_NAME_KEY),
                        isAnonymous = localStorage.getItem(IS_ANONYMOUS_KEY)?.toBoolean() ?: false
                    )
                )
            } else {
                AuthState.Unauthenticated
            }
        } catch (e: Exception) {
            AuthState.Unauthenticated
        }
    }

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

        saveUser(user)
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
            id = generateUUID(),
            email = null,
            displayName = "Guest",
            isAnonymous = true
        )

        saveUser(user)
        _authState.value = AuthState.Authenticated(user)
        return AuthResult.Success(user)
    }

    override suspend fun signOut() {
        clearUser()
        _authState.value = AuthState.Unauthenticated
    }

    override suspend fun sendPasswordResetEmail(email: String): AuthResult {
        delay(300)
        return AuthResult.Success(User(id = "", email = email, displayName = null))
    }

    private fun generateUserId(email: String): String {
        return "web_${email.hashCode()}_${generateUUID().take(8)}"
    }

    private fun generateUUID(): String {
        val chars = "0123456789abcdef"
        return buildString {
            repeat(32) {
                append(chars[Random.nextInt(chars.length)])
                if (it == 7 || it == 11 || it == 15 || it == 19) append("-")
            }
        }
    }

    private fun saveUser(user: User) {
        try {
            localStorage.setItem(USER_ID_KEY, user.id)
            user.email?.let { localStorage.setItem(USER_EMAIL_KEY, it) }
            user.displayName?.let { localStorage.setItem(USER_NAME_KEY, it) }
            localStorage.setItem(IS_ANONYMOUS_KEY, user.isAnonymous.toString())
        } catch (e: Exception) {
            // Handle error silently
        }
    }

    private fun clearUser() {
        try {
            localStorage.removeItem(USER_ID_KEY)
            localStorage.removeItem(USER_EMAIL_KEY)
            localStorage.removeItem(USER_NAME_KEY)
            localStorage.removeItem(IS_ANONYMOUS_KEY)
        } catch (e: Exception) {
            // Handle error silently
        }
    }
}
