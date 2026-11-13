package com.dhananjay.livecast.auth

import kotlinx.coroutines.test.runTest
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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

    private fun makeJwt(expEpochSeconds: Long): String {
        val header = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"
        val payload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("""{"exp":$expEpochSeconds}""".toByteArray(Charsets.UTF_8))
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
}
