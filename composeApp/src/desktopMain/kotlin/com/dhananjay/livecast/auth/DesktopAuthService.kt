package com.dhananjay.livecast.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Properties

/**
 * Desktop (JVM) implementation of AuthService using Firebase Auth REST API.
 * Connects to Firebase Authentication backend for real authentication.
 * Requires FIREBASE_API_KEY environment variable or in .livecast/config.properties
 */
class DesktopAuthService : AuthService {

    private val configDir = File(System.getProperty("user.home"), ".livecast")
    private val authFile = File(configDir, "auth.properties")
    private val configFile = File(configDir, "config.properties")

    private val firebaseApiKey: String? by lazy {
        System.getenv("FIREBASE_API_KEY") ?: loadConfigProperty("firebase_api_key")
    }

    private val json = Json { ignoreUnknownKeys = true }

    private val _authState = MutableStateFlow<AuthState>(loadInitialState())
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    override val currentUser: User?
        get() = (_authState.value as? AuthState.Authenticated)?.user

    init {
        if (!configDir.exists()) {
            configDir.mkdirs()
        }
    }

    private fun loadConfigProperty(key: String): String? {
        return try {
            if (configFile.exists()) {
                val props = Properties().apply {
                    configFile.inputStream().use { load(it) }
                }
                props.getProperty(key)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun loadInitialState(): AuthState {
        return try {
            if (authFile.exists()) {
                val props = Properties().apply {
                    authFile.inputStream().use { load(it) }
                }
                val userId = props.getProperty("user_id")
                val idToken = props.getProperty("id_token")
                if (userId != null && idToken != null) {
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
        if (firebaseApiKey == null) {
            return AuthResult.Error("Firebase API key not configured. Set FIREBASE_API_KEY environment variable or add firebase_api_key to ~/.livecast/config.properties")
        }

        _authState.value = AuthState.Loading

        return withContext(Dispatchers.IO) {
            try {
                val url = URL("https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=$firebaseApiKey")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true

                @Serializable
                data class SignInRequest(val email: String, val password: String, val returnSecureToken: Boolean = true)
                
                val requestBody = json.encodeToString(SignInRequest.serializer(), SignInRequest(email, password))

                connection.outputStream.use { it.write(requestBody.toByteArray()) }

                val responseCode = connection.responseCode
                val responseBody = if (responseCode == 200) {
                    connection.inputStream.bufferedReader().use { it.readText() }
                } else {
                    connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                }

                if (responseCode == 200) {
                    val response = json.decodeFromString<FirebaseAuthResponse>(responseBody)
                    val user = User(
                        id = response.localId,
                        email = response.email,
                        displayName = response.displayName,
                        isAnonymous = false
                    )
                    saveUser(user, response.idToken)
                    _authState.value = AuthState.Authenticated(user)
                    AuthResult.Success(user)
                } else {
                    val errorMessage = try {
                        val errorResponse = json.decodeFromString<FirebaseErrorResponse>(responseBody)
                        errorResponse.error.message
                    } catch (e: Exception) {
                        "Authentication failed"
                    }
                    _authState.value = AuthState.Error(errorMessage)
                    AuthResult.Error(errorMessage)
                }
            } catch (e: Exception) {
                val errorMessage = e.message ?: "Authentication failed"
                _authState.value = AuthState.Error(errorMessage)
                AuthResult.Error(errorMessage, e)
            }
        }
    }

    override suspend fun createUserWithEmailAndPassword(email: String, password: String): AuthResult {
        if (firebaseApiKey == null) {
            return AuthResult.Error("Firebase API key not configured. Set FIREBASE_API_KEY environment variable or add firebase_api_key to ~/.livecast/config.properties")
        }

        _authState.value = AuthState.Loading

        return withContext(Dispatchers.IO) {
            try {
                val url = URL("https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=$firebaseApiKey")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true

                @Serializable
                data class SignUpRequest(val email: String, val password: String, val returnSecureToken: Boolean = true)
                
                val requestBody = json.encodeToString(SignUpRequest.serializer(), SignUpRequest(email, password))

                connection.outputStream.use { it.write(requestBody.toByteArray()) }

                val responseCode = connection.responseCode
                val responseBody = if (responseCode == 200) {
                    connection.inputStream.bufferedReader().use { it.readText() }
                } else {
                    connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                }

                if (responseCode == 200) {
                    val response = json.decodeFromString<FirebaseAuthResponse>(responseBody)
                    val user = User(
                        id = response.localId,
                        email = response.email,
                        displayName = response.displayName,
                        isAnonymous = false
                    )
                    saveUser(user, response.idToken)
                    _authState.value = AuthState.Authenticated(user)
                    AuthResult.Success(user)
                } else {
                    val errorMessage = try {
                        val errorResponse = json.decodeFromString<FirebaseErrorResponse>(responseBody)
                        errorResponse.error.message
                    } catch (e: Exception) {
                        "Account creation failed"
                    }
                    _authState.value = AuthState.Error(errorMessage)
                    AuthResult.Error(errorMessage)
                }
            } catch (e: Exception) {
                val errorMessage = e.message ?: "Account creation failed"
                _authState.value = AuthState.Error(errorMessage)
                AuthResult.Error(errorMessage, e)
            }
        }
    }

    override suspend fun signInAnonymously(): AuthResult {
        if (firebaseApiKey == null) {
            return AuthResult.Error("Firebase API key not configured. Set FIREBASE_API_KEY environment variable or add firebase_api_key to ~/.livecast/config.properties")
        }

        _authState.value = AuthState.Loading

        return withContext(Dispatchers.IO) {
            try {
                val url = URL("https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=$firebaseApiKey")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true

                @Serializable
                data class AnonymousSignInRequest(val returnSecureToken: Boolean = true)
                
                val requestBody = json.encodeToString(AnonymousSignInRequest.serializer(), AnonymousSignInRequest())

                connection.outputStream.use { it.write(requestBody.toByteArray()) }

                val responseCode = connection.responseCode
                val responseBody = if (responseCode == 200) {
                    connection.inputStream.bufferedReader().use { it.readText() }
                } else {
                    connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                }

                if (responseCode == 200) {
                    val response = json.decodeFromString<FirebaseAuthResponse>(responseBody)
                    val user = User(
                        id = response.localId,
                        email = null,
                        displayName = "Guest",
                        isAnonymous = true
                    )
                    saveUser(user, response.idToken)
                    _authState.value = AuthState.Authenticated(user)
                    AuthResult.Success(user)
                } else {
                    val errorMessage = try {
                        val errorResponse = json.decodeFromString<FirebaseErrorResponse>(responseBody)
                        errorResponse.error.message
                    } catch (e: Exception) {
                        "Anonymous sign in failed"
                    }
                    _authState.value = AuthState.Error(errorMessage)
                    AuthResult.Error(errorMessage)
                }
            } catch (e: Exception) {
                val errorMessage = e.message ?: "Anonymous sign in failed"
                _authState.value = AuthState.Error(errorMessage)
                AuthResult.Error(errorMessage, e)
            }
        }
    }

    override suspend fun signOut() {
        clearUser()
        _authState.value = AuthState.Unauthenticated
    }

    override suspend fun sendPasswordResetEmail(email: String): AuthResult {
        if (firebaseApiKey == null) {
            return AuthResult.Error("Firebase API key not configured. Set FIREBASE_API_KEY environment variable or add firebase_api_key to ~/.livecast/config.properties")
        }

        return withContext(Dispatchers.IO) {
            try {
                val url = URL("https://identitytoolkit.googleapis.com/v1/accounts:sendOobCode?key=$firebaseApiKey")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true

                @Serializable
                data class PasswordResetRequest(val requestType: String, val email: String)
                
                val requestBody = json.encodeToString(
                    PasswordResetRequest.serializer(), 
                    PasswordResetRequest("PASSWORD_RESET", email)
                )

                connection.outputStream.use { it.write(requestBody.toByteArray()) }

                val responseCode = connection.responseCode
                val responseBody = if (responseCode == 200) {
                    connection.inputStream.bufferedReader().use { it.readText() }
                } else {
                    connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                }

                if (responseCode == 200) {
                    // Password reset email sent successfully
                    // Note: Returns a minimal User object for API consistency - the empty ID indicates
                    // this is not an authenticated user but rather a confirmation of the email operation
                    AuthResult.Success(User(id = "", email = email, displayName = null))
                } else {
                    val errorMessage = try {
                        val errorResponse = json.decodeFromString<FirebaseErrorResponse>(responseBody)
                        errorResponse.error.message
                    } catch (e: Exception) {
                        "Failed to send password reset email"
                    }
                    AuthResult.Error(errorMessage)
                }
            } catch (e: Exception) {
                AuthResult.Error(e.message ?: "Failed to send password reset email", e)
            }
        }
    }

    private fun saveUser(user: User, idToken: String) {
        try {
            if (!configDir.exists()) configDir.mkdirs()
            val props = Properties().apply {
                setProperty("user_id", user.id)
                user.email?.let { setProperty("email", it) }
                user.displayName?.let { setProperty("display_name", it) }
                setProperty("is_anonymous", user.isAnonymous.toString())
                setProperty("id_token", idToken)
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

    @Serializable
    private data class FirebaseAuthResponse(
        val idToken: String,
        val email: String? = null,
        val refreshToken: String,
        val expiresIn: String,
        val localId: String,
        val displayName: String? = null
    )

    @Serializable
    private data class FirebaseErrorResponse(
        val error: FirebaseError
    )

    @Serializable
    private data class FirebaseError(
        val code: Int,
        val message: String,
        val errors: List<FirebaseErrorDetail>? = null
    )

    @Serializable
    private data class FirebaseErrorDetail(
        val message: String,
        val domain: String,
        val reason: String
    )
}
