package com.dhananjay.livecast.auth

import kotlinx.coroutines.test.runTest
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopAuthServiceTokenTest {

    private class FakeAuthSessionStore(var session: StoredSession? = null) : AuthSessionStore {
        var saveCalls = 0
        var updateCalls = 0
        var clearCalls = 0

        override fun load(): StoredSession? = session

        override fun save(session: StoredSession) {
            saveCalls++
            this.session = session
        }

        override fun updateTokens(idToken: String, refreshToken: String?) {
            updateCalls++
            val s = session
            if (s != null) {
                session = s.copy(idToken = idToken, refreshToken = refreshToken ?: s.refreshToken)
            }
        }

        override fun clear() {
            clearCalls++
            session = null
        }
    }

    private class FakeTokenRefresher(var result: Pair<String, String?>? = null) : TokenRefresher {
        var refreshCalls = 0
        var lastRefreshToken: String? = null

        override suspend fun refresh(refreshToken: String): Pair<String, String?>? {
            refreshCalls++
            lastRefreshToken = refreshToken
            return result
        }
    }

    private class FakeVerificationEmailSender(
        var result: Result<Unit> = Result.success(Unit)
    ) : VerificationEmailSender {
        var sendCalls = 0
        var lastIdToken: String? = null

        override suspend fun send(idToken: String): Result<Unit> {
            sendCalls++
            lastIdToken = idToken
            return result
        }
    }

    private fun makeJwt(expEpochSeconds: Long, emailVerified: Boolean? = null): String {
        val header = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"
        val body = buildString {
            append("""{"exp":$expEpochSeconds""")
            if (emailVerified != null) {
                append(""","email_verified":$emailVerified""")
            }
            append("}")
        }
        val payload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(body.toByteArray(Charsets.UTF_8))
        return "$header.$payload.signature"
    }

    private val testUser = User(id = "user-1", email = "test@example.com", displayName = "Test User")

    @Test
    fun freshTokenReturnedWithoutRefresh() = runTest {
        val token = makeJwt(2000L)
        val store = FakeAuthSessionStore(StoredSession(testUser, token, "rt-123"))
        val refresher = FakeTokenRefresher(Pair("new-token", "new-rt"))

        val authService = DesktopAuthService(
            store = store,
            refresher = refresher,
            clockEpochSeconds = { 1000L }
        )

        val idToken = authService.getIdToken(forceRefresh = false)
        assertEquals(token, idToken)
        assertEquals(0, refresher.refreshCalls)
        assertEquals(0, store.updateCalls)
    }

    @Test
    fun expiringTokenRefreshedAndStored() = runTest {
        val token = makeJwt(2000L)
        val store = FakeAuthSessionStore(StoredSession(testUser, token, "rt-123"))
        val refresher = FakeTokenRefresher(Pair("refreshed-token", "new-rt"))

        val authService = DesktopAuthService(
            store = store,
            refresher = refresher,
            clockEpochSeconds = { 1800L } // within skew (2000 - 300 = 1700 <= 1800)
        )

        val idToken = authService.getIdToken(forceRefresh = false)
        assertEquals("refreshed-token", idToken)
        assertEquals(1, refresher.refreshCalls)
        assertEquals("rt-123", refresher.lastRefreshToken)
        assertEquals(1, store.updateCalls)
        assertEquals("refreshed-token", store.session?.idToken)
        assertEquals("new-rt", store.session?.refreshToken)
    }

    @Test
    fun forceRefreshAlwaysRefreshes() = runTest {
        val token = makeJwt(2000L)
        val store = FakeAuthSessionStore(StoredSession(testUser, token, "rt-123"))
        val refresher = FakeTokenRefresher(Pair("forced-refreshed-token", "new-rt"))

        val authService = DesktopAuthService(
            store = store,
            refresher = refresher,
            clockEpochSeconds = { 1000L } // fresh!
        )

        val idToken = authService.getIdToken(forceRefresh = true)
        assertEquals("forced-refreshed-token", idToken)
        assertEquals(1, refresher.refreshCalls)
        assertEquals(1, store.updateCalls)
        assertEquals("forced-refreshed-token", store.session?.idToken)
    }

    @Test
    fun noRefreshTokenReturnsNull() = runTest {
        val token = makeJwt(2000L)
        val store = FakeAuthSessionStore(StoredSession(testUser, token, refreshToken = null))
        val refresher = FakeTokenRefresher(Pair("refreshed-token", "new-rt"))

        val authService = DesktopAuthService(
            store = store,
            refresher = refresher,
            clockEpochSeconds = { 1800L } // needs refresh
        )

        val idToken = authService.getIdToken(forceRefresh = false)
        assertNull(idToken)
        assertEquals(0, refresher.refreshCalls)
        assertEquals(0, store.updateCalls)
    }

    @Test
    fun refreshEmailVerificationForcesTokenRefresh() = runTest {
        val initialToken = makeJwt(2000L, emailVerified = false)
        val store = FakeAuthSessionStore(StoredSession(testUser.copy(emailVerified = false), initialToken, "rt-123"))
        val refreshedToken = makeJwt(3000L, emailVerified = true)
        val refresher = FakeTokenRefresher(Pair(refreshedToken, "new-rt"))

        val authService = DesktopAuthService(
            store = store,
            refresher = refresher,
            clockEpochSeconds = { 1000L } // not near expiry (2000 - 300 = 1700 > 1000)
        )

        val result = authService.refreshEmailVerification()
        assertTrue(result.isSuccess)
        assertEquals(true, result.getOrNull())
        assertEquals(1, refresher.refreshCalls)
        val user = (authService.authState.value as AuthState.Authenticated).user
        assertTrue(user.emailVerified)
        assertTrue(authService.currentUser!!.emailVerified)
    }

    @Test
    fun refreshEmailVerificationKeepsRotatedRefreshToken() = runTest {
        val initialToken = makeJwt(2000L, emailVerified = false)
        val store = FakeAuthSessionStore(StoredSession(testUser.copy(emailVerified = false), initialToken, "old-rt"))
        val refreshedToken = makeJwt(3000L, emailVerified = true)
        val refresher = FakeTokenRefresher(Pair(refreshedToken, "new-rt"))

        val authService = DesktopAuthService(
            store = store,
            refresher = refresher,
            clockEpochSeconds = { 1000L }
        )

        val result = authService.refreshEmailVerification()
        assertTrue(result.isSuccess)
        assertEquals("new-rt", store.session?.refreshToken)
        assertEquals(refreshedToken, store.session?.idToken)
    }

    @Test
    fun refreshEmailVerificationSignedOut() = runTest {
        val store = FakeAuthSessionStore(session = null)
        val refresher = FakeTokenRefresher(Pair("token", "rt"))

        val authService = DesktopAuthService(
            store = store,
            refresher = refresher
        )

        val result = authService.refreshEmailVerification()
        assertTrue(result.isFailure)
        assertEquals(0, refresher.refreshCalls)
    }

    @Test
    fun sendVerificationEmailUsesCurrentToken() = runTest {
        val token = makeJwt(2000L)
        val store = FakeAuthSessionStore(StoredSession(testUser, token, "rt-123"))
        val refresher = FakeTokenRefresher()
        val sender = FakeVerificationEmailSender(result = Result.success(Unit))

        val authService = DesktopAuthService(
            store = store,
            refresher = refresher,
            verificationSender = sender,
            clockEpochSeconds = { 1000L }
        )

        val result = authService.sendVerificationEmail()
        assertEquals(1, sender.sendCalls)
        assertEquals(token, sender.lastIdToken)
        assertTrue(result is AuthResult.Success)
    }

    @Test
    fun sendVerificationEmailSignedOut() = runTest {
        val store = FakeAuthSessionStore(session = null)
        val refresher = FakeTokenRefresher()
        val sender = FakeVerificationEmailSender()

        val authService = DesktopAuthService(
            store = store,
            refresher = refresher,
            verificationSender = sender
        )

        val result = authService.sendVerificationEmail()
        assertTrue(result is AuthResult.Error)
        assertEquals(0, sender.sendCalls)
    }

    @Test
    fun loadedSessionCarriesEmailVerified() = runTest {
        val token = makeJwt(2000L, emailVerified = true)
        val store = FakeAuthSessionStore(StoredSession(testUser.copy(emailVerified = false), token, "rt-123"))

        val authService = DesktopAuthService(
            store = store,
            refresher = FakeTokenRefresher(),
            clockEpochSeconds = { 1000L }
        )

        assertEquals(true, authService.currentUser?.emailVerified)
        val authUser = (authService.authState.value as AuthState.Authenticated).user
        assertTrue(authUser.emailVerified)
    }
}
