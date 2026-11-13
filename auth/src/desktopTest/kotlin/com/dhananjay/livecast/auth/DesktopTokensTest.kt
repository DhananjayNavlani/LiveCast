package com.dhananjay.livecast.auth

import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopTokensTest {

    @Test
    fun expiryParsed() {
        val header = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"
        val payload = Base64.getUrlEncoder().withoutPadding().encodeToString("""{"exp":2000}""".toByteArray(Charsets.UTF_8))
        val token = "$header.$payload.signature"
        val expiry = jwtExpiryEpochSeconds(token)
        assertEquals(2000L, expiry)
    }

    @Test
    fun malformedIsNull() {
        assertNull(jwtExpiryEpochSeconds("not-a-jwt"))
        assertNull(jwtExpiryEpochSeconds("header.payload"))
        assertNull(jwtExpiryEpochSeconds("header.invalid_base64!@#.signature"))
        val header = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"
        val noExpPayload = Base64.getUrlEncoder().withoutPadding().encodeToString("""{"sub":"123"}""".toByteArray(Charsets.UTF_8))
        assertNull(jwtExpiryEpochSeconds("$header.$noExpPayload.signature"))
    }

    @Test
    fun needsRefreshWithinSkew() {
        val header = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"
        val payload = Base64.getUrlEncoder().withoutPadding().encodeToString("""{"exp":2000}""".toByteArray(Charsets.UTF_8))
        val token = "$header.$payload.signature"

        // Exp 2000, skew 300 -> threshold 1700
        assertTrue(needsRefresh(token, nowEpochSeconds = 1800L))
        assertFalse(needsRefresh(token, nowEpochSeconds = 1600L))
    }

    @Test
    fun desktopAuthServiceReturnsStoredTokenWhenValid() = kotlinx.coroutines.test.runTest {
        val tempDir = java.nio.file.Files.createTempDirectory("livecast-test").toFile()
        try {
            val futureExp = (System.currentTimeMillis() / 1000L) + 3600L
            val header = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"
            val payload = Base64.getUrlEncoder().withoutPadding().encodeToString("""{"exp":$futureExp}""".toByteArray(Charsets.UTF_8))
            val token = "$header.$payload.signature"

            val authFile = java.io.File(tempDir, "auth.properties")
            val props = java.util.Properties().apply {
                setProperty("id_token", token)
                setProperty("refresh_token", "dummy-refresh")
            }
            authFile.outputStream().use { props.store(it, null) }

            val authService = DesktopAuthService(configDir = tempDir)
            assertEquals(token, authService.getIdToken(forceRefresh = false))
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
