package com.dhananjay.livecast.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.util.Properties

/**
 * Desktop (JVM) implementation of AuthService using Firebase Auth REST API.
 * Follows SOLID principles: session storage is delegated to [AuthSessionStore],
 * token refreshing is delegated to [TokenRefresher].
 */
class DesktopAuthService(
    private val store: AuthSessionStore,
    private val refresher: TokenRefresher,
    private val apiKeyProvider: () -> String? = { null },
    private val verificationSender: VerificationEmailSender = RestVerificationEmailSender(apiKeyProvider),
    private val clockEpochSeconds: () -> Long = { System.currentTimeMillis() / 1000L }
) : AuthService {

    constructor(
        configDir: File = File(System.getProperty("user.home"), ".livecast"),
        clockEpochSeconds: () -> Long = { System.currentTimeMillis() / 1000L }
    ) : this(
        store = PropertiesAuthSessionStore(configDir),
        refresher = SecureTokenRefresher({ loadApiKeyFromConfig(configDir) }),
        apiKeyProvider = { loadApiKeyFromConfig(configDir) },
        verificationSender = RestVerificationEmailSender({ loadApiKeyFromConfig(configDir) }),
        clockEpochSeconds = clockEpochSeconds
    )

    private val json = Json { ignoreUnknownKeys = true }

    private val _authState = MutableStateFlow<AuthState>(
        store.load()?.let { session ->
            val user = session.user.copy(emailVerified = jwtEmailVerified(session.idToken))
            AuthState.Authenticated(user)
        } ?: AuthState.Unauthenticated
    )
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    override val currentUser: User?
        get() = (_authState.value as? AuthState.Authenticated)?.user

    override fun isLoggedIn(): Boolean = currentUser != null

    override suspend fun signInWithEmailAndPassword(email: String, password: String): AuthResult {
        val apiKey = apiKeyProvider()
        if (apiKey.isNullOrBlank()) {
            return AuthResult.Error("Firebase API key not configured. Set FIREBASE_API_KEY environment variable or add firebase_api_key to ~/.livecast/config.properties")
        }

        _authState.value = AuthState.Loading

        return withContext(Dispatchers.IO) {
            try {
                val url = URI("https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=$apiKey").toURL()
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
                        isAnonymous = false,
                        emailVerified = jwtEmailVerified(response.idToken)
                    )
                    store.save(StoredSession(user, response.idToken, response.refreshToken))
                    _authState.value = AuthState.Authenticated(user)
                    AuthResult.Success(user)
                } else {
                    val errorMessage = try {
                        val errorResponse = json.decodeFromString<FirebaseErrorResponse>(responseBody)
                        errorResponse.error.message
                    } catch (_: Exception) {
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
        val apiKey = apiKeyProvider()
        if (apiKey.isNullOrBlank()) {
            return AuthResult.Error("Firebase API key not configured. Set FIREBASE_API_KEY environment variable or add firebase_api_key to ~/.livecast/config.properties")
        }

        _authState.value = AuthState.Loading

        return withContext(Dispatchers.IO) {
            try {
                val url = URI("https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=$apiKey").toURL()
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
                        isAnonymous = false,
                        emailVerified = jwtEmailVerified(response.idToken)
                    )
                    store.save(StoredSession(user, response.idToken, response.refreshToken))
                    _authState.value = AuthState.Authenticated(user)
                    AuthResult.Success(user)
                } else {
                    val errorMessage = try {
                        val errorResponse = json.decodeFromString<FirebaseErrorResponse>(responseBody)
                        errorResponse.error.message
                    } catch (_: Exception) {
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
        val apiKey = apiKeyProvider()
        if (apiKey.isNullOrBlank()) {
            return AuthResult.Error("Firebase API key not configured. Set FIREBASE_API_KEY environment variable or add firebase_api_key to ~/.livecast/config.properties")
        }

        _authState.value = AuthState.Loading

        return withContext(Dispatchers.IO) {
            try {
                val url = URI("https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=$apiKey").toURL()
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
                    store.save(StoredSession(user, response.idToken, response.refreshToken))
                    _authState.value = AuthState.Authenticated(user)
                    AuthResult.Success(user)
                } else {
                    val errorMessage = try {
                        val errorResponse = json.decodeFromString<FirebaseErrorResponse>(responseBody)
                        errorResponse.error.message
                    } catch (_: Exception) {
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
        store.clear()
        _authState.value = AuthState.Unauthenticated
    }

    override suspend fun sendPasswordResetEmail(email: String): AuthResult {
        val apiKey = apiKeyProvider()
        if (apiKey.isNullOrBlank()) {
            return AuthResult.Error("Firebase API key not configured. Set FIREBASE_API_KEY environment variable or add firebase_api_key to ~/.livecast/config.properties")
        }

        return withContext(Dispatchers.IO) {
            try {
                val url = URI("https://identitytoolkit.googleapis.com/v1/accounts:sendOobCode?key=$apiKey").toURL()
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
                    AuthResult.Success(User(id = "", email = email, displayName = null))
                } else {
                    val errorMessage = try {
                        val errorResponse = json.decodeFromString<FirebaseErrorResponse>(responseBody)
                        errorResponse.error.message
                    } catch (_: Exception) {
                        "Failed to send password reset email"
                    }
                    AuthResult.Error(errorMessage)
                }
            } catch (e: Exception) {
                AuthResult.Error(e.message ?: "Failed to send password reset email", e)
            }
        }
    }

    override suspend fun getIdToken(forceRefresh: Boolean): String? {
        val session = store.load() ?: return null
        val nowEpochSeconds = clockEpochSeconds()

        if (!forceRefresh && !needsRefresh(session.idToken, nowEpochSeconds)) {
            return session.idToken
        }

        val refreshToken = session.refreshToken
        if (refreshToken.isNullOrBlank()) {
            return null
        }

        val refreshed = refresher.refresh(refreshToken) ?: return null
        val (newIdToken, newRefreshToken) = refreshed
        store.updateTokens(newIdToken, newRefreshToken ?: refreshToken)
        val current = currentUser
        if (current != null) {
            val updatedUser = current.copy(emailVerified = jwtEmailVerified(newIdToken))
            _authState.value = AuthState.Authenticated(updatedUser)
        }
        return newIdToken
    }

    override suspend fun sendVerificationEmail(): AuthResult {
        val user = currentUser
        if (user == null || store.load() == null) {
            return AuthResult.Error("Not signed in")
        }
        val idToken = getIdToken(forceRefresh = false)
            ?: return AuthResult.Error("Failed to obtain ID token")

        val result = verificationSender.send(idToken)
        return result.fold(
            onSuccess = { AuthResult.Success(user) },
            onFailure = { e -> AuthResult.Error(e.message ?: "Failed to send verification email", e) }
        )
    }

    override suspend fun refreshEmailVerification(): Result<Boolean> {
        val session = store.load()
            ?: return Result.failure(IllegalStateException("Not signed in"))

        val newIdToken = getIdToken(forceRefresh = true)
            ?: return Result.failure(IllegalStateException("Failed to refresh token"))

        val verified = jwtEmailVerified(newIdToken)
        val updatedUser = (currentUser ?: session.user).copy(emailVerified = verified)
        store.save(session.copy(user = updatedUser, idToken = newIdToken))
        _authState.value = AuthState.Authenticated(updatedUser)
        return Result.success(verified)
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

    companion object {
        fun loadApiKeyFromConfig(configDir: File): String? {
            System.getenv("FIREBASE_API_KEY")?.takeIf { it.isNotBlank() }?.let { return it }
            val configFile = File(configDir, "config.properties")
            return try {
                if (configFile.exists()) {
                    val props = Properties().apply {
                        configFile.inputStream().use { load(it) }
                    }
                    props.getProperty("firebase_api_key")?.takeIf { it.isNotBlank() }
                } else null
            } catch (_: Exception) {
                null
            }
        }
    }
}

@Serializable
internal data class FirebaseErrorResponse(
    val error: FirebaseError
)

@Serializable
internal data class FirebaseError(
    val code: Int,
    val message: String,
    val errors: List<FirebaseErrorDetail>? = null
)

@Serializable
internal data class FirebaseErrorDetail(
    val message: String,
    val domain: String,
    val reason: String
)
