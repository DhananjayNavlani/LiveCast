package com.dhananjay.livecast.auth

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.Properties
import java.util.UUID

/**
 * Desktop (JVM) implementation of AuthService.
 * Uses local file storage for persistence.
 */
class DesktopAuthService : AuthService {

    private val configDir = File(System.getProperty("user.home"), ".livecast")
    private val authFile = File(configDir, "auth.properties")

    private val _authState = MutableStateFlow<AuthState>(loadInitialState())
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    override val currentUser: User?
        get() = (_authState.value as? AuthState.Authenticated)?.user

    init {
        if (!configDir.exists()) {
            configDir.mkdirs()
        }
    }

    private fun loadInitialState(): AuthState {
        return try {
            if (authFile.exists()) {
                val props = Properties().apply {
                    authFile.inputStream().use { load(it) }
                }
                val userId = props.getProperty("user_id")
                if (userId != null) {
                    AuthState.Authenticated(
                        User(
                            id = userId,
                            email = props.getProperty("email"),
                            displayName = props.getProperty("display_name"),
                            isAnonymous = props.getProperty("is_anonymous")?.toBoolean() ?: false
                        )
                    )
                } else {
                    AuthState.Unauthenticated
                }
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
            id = UUID.randomUUID().toString(),
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
        return "desktop_${email.hashCode()}_${UUID.randomUUID().toString().take(8)}"
    }

    private fun saveUser(user: User) {
        try {
            if (!configDir.exists()) configDir.mkdirs()
            val props = Properties().apply {
                setProperty("user_id", user.id)
                user.email?.let { setProperty("email", it) }
                user.displayName?.let { setProperty("display_name", it) }
                setProperty("is_anonymous", user.isAnonymous.toString())
            }
            authFile.outputStream().use { props.store(it, "LiveCast Auth") }
        } catch (e: Exception) {
            // Handle error silently
        }
    }

    private fun clearUser() {
        try {
            if (authFile.exists()) {
                authFile.delete()
            }
        } catch (e: Exception) {
            // Handle error silently
        }
    }
}
