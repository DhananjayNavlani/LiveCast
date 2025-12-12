package com.dhananjay.livecast.auth

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUUID

/**
 * iOS implementation of AuthService.
 * Uses NSUserDefaults for persistence.
 */
class IosAuthService : AuthService {

    private val userDefaults = NSUserDefaults.standardUserDefaults
    private val USER_ID_KEY = "livecast_user_id"
    private val USER_EMAIL_KEY = "livecast_user_email"
    private val USER_NAME_KEY = "livecast_user_name"
    private val IS_ANONYMOUS_KEY = "livecast_is_anonymous"

    private val _authState = MutableStateFlow<AuthState>(loadInitialState())
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    override val currentUser: User?
        get() = (_authState.value as? AuthState.Authenticated)?.user

    private fun loadInitialState(): AuthState {
        val userId = userDefaults.stringForKey(USER_ID_KEY)
        return if (userId != null) {
            AuthState.Authenticated(
                User(
                    id = userId,
                    email = userDefaults.stringForKey(USER_EMAIL_KEY),
                    displayName = userDefaults.stringForKey(USER_NAME_KEY),
                    isAnonymous = userDefaults.boolForKey(IS_ANONYMOUS_KEY)
                )
            )
        } else {
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
            id = NSUUID().UUIDString,
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
        return "ios_${email.hashCode()}_${NSUUID().UUIDString.take(8)}"
    }

    private fun saveUser(user: User) {
        userDefaults.setObject(user.id, USER_ID_KEY)
        user.email?.let { userDefaults.setObject(it, USER_EMAIL_KEY) }
        user.displayName?.let { userDefaults.setObject(it, USER_NAME_KEY) }
        userDefaults.setBool(user.isAnonymous, IS_ANONYMOUS_KEY)
        userDefaults.synchronize()
    }

    private fun clearUser() {
        userDefaults.removeObjectForKey(USER_ID_KEY)
        userDefaults.removeObjectForKey(USER_EMAIL_KEY)
        userDefaults.removeObjectForKey(USER_NAME_KEY)
        userDefaults.removeObjectForKey(IS_ANONYMOUS_KEY)
        userDefaults.synchronize()
    }
}
