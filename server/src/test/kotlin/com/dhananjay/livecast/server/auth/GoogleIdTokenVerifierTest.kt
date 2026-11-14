package com.dhananjay.livecast.server.auth

import com.dhananjay.livecast.server.Clock
import com.dhananjay.livecast.server.PublicKeySource
import com.dhananjay.livecast.server.VerifiedCaller
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.PlainJWT
import com.nimbusds.jwt.SignedJWT
import kotlinx.coroutines.test.runTest
import java.security.interfaces.RSAPublicKey
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GoogleIdTokenVerifierTest {

    private val rsaKey = RSAKeyGenerator(2048).keyID("k1").generate()
    private val publicKey = rsaKey.toRSAPublicKey()
    private val signer = RSASSASigner(rsaKey)

    private class FakeKeySource(val map: Map<String, RSAPublicKey>) : PublicKeySource {
        override suspend fun keys(): Map<String, RSAPublicKey> = map
    }

    private class FakeClock(var now: Long = 1_000_000_000L) : Clock {
        override fun nowMs(): Long = now
    }

    private val projectId = "test-project"
    private val clock = FakeClock()
    private val keySource = FakeKeySource(mapOf("k1" to publicKey))

    private fun buildToken(
        kid: String? = "k1",
        issuer: String = "https://securetoken.google.com/$projectId",
        audience: String = projectId,
        subject: String = "u1",
        email: String? = "user@example.com",
        emailVerified: Any? = true,
        issueTime: Date = Date(clock.nowMs() - 10_000L),
        expirationTime: Date = Date(clock.nowMs() + 3600_000L),
        customSigner: RSASSASigner? = signer,
    ): String {
        val claimsBuilder = JWTClaimsSet.Builder()
            .issuer(issuer)
            .audience(audience)
            .subject(subject)
            .issueTime(issueTime)
            .expirationTime(expirationTime)

        if (email != null) claimsBuilder.claim("email", email)
        if (emailVerified != null) claimsBuilder.claim("email_verified", emailVerified)

        val headerBuilder = JWSHeader.Builder(JWSAlgorithm.RS256)
        if (kid != null) headerBuilder.keyID(kid)

        val jwt = SignedJWT(headerBuilder.build(), claimsBuilder.build())
        jwt.sign(customSigner ?: signer)
        return jwt.serialize()
    }

    @Test
    fun validTokenVerified() = runTest {
        val verifier = GoogleIdTokenVerifier(keySource, projectId, clock)
        val token = buildToken()
        val caller = verifier.verify(token)
        assertEquals(VerifiedCaller(uid = "u1", email = "user@example.com"), caller)
    }

    @Test
    fun wrongAudienceRejected() = runTest {
        val verifier = GoogleIdTokenVerifier(keySource, projectId, clock)
        val token = buildToken(audience = "wrong-project")
        assertNull(verifier.verify(token))
    }

    @Test
    fun multipleAudiencesRejected() = runTest {
        val verifier = GoogleIdTokenVerifier(keySource, projectId, clock)
        val claims = JWTClaimsSet.Builder()
            .issuer("https://securetoken.google.com/$projectId")
            .audience(listOf(projectId, "another-audience"))
            .subject("u1")
            .claim("email", "user@example.com")
            .claim("email_verified", true)
            .issueTime(Date(clock.nowMs()))
            .expirationTime(Date(clock.nowMs() + 3600_000L))
            .build()
        val jwt = SignedJWT(JWSHeader.Builder(JWSAlgorithm.RS256).keyID("k1").build(), claims)
        jwt.sign(signer)
        assertNull(verifier.verify(jwt.serialize()))
    }

    @Test
    fun wrongIssuerRejected() = runTest {
        val verifier = GoogleIdTokenVerifier(keySource, projectId, clock)
        val token = buildToken(issuer = "https://wrong.issuer.com")
        assertNull(verifier.verify(token))
    }

    @Test
    fun expiredRejected() = runTest {
        val verifier = GoogleIdTokenVerifier(keySource, projectId, clock)
        // expired 70 seconds ago (outside 60s tolerance)
        val token = buildToken(expirationTime = Date(clock.nowMs() - 70_000L))
        assertNull(verifier.verify(token))
    }

    @Test
    fun unverifiedEmailRejected() = runTest {
        val verifier = GoogleIdTokenVerifier(keySource, projectId, clock)
        val tokenFalse = buildToken(emailVerified = false)
        assertNull(verifier.verify(tokenFalse))

        val tokenNull = buildToken(emailVerified = null)
        assertNull(verifier.verify(tokenNull))
    }

    @Test
    fun stringEmailVerifiedRejected() = runTest {
        val verifier = GoogleIdTokenVerifier(keySource, projectId, clock)
        val token = buildToken(emailVerified = "true")
        assertNull(verifier.verify(token))
    }

    @Test
    fun unknownKidRejected() = runTest {
        val verifier = GoogleIdTokenVerifier(keySource, projectId, clock)
        val token = buildToken(kid = "unknown-k2")
        assertNull(verifier.verify(token))
    }

    @Test
    fun badSignatureRejected() = runTest {
        val otherKey = RSAKeyGenerator(2048).keyID("k1").generate()
        val otherSigner = RSASSASigner(otherKey)
        val verifier = GoogleIdTokenVerifier(keySource, projectId, clock)
        val token = buildToken(customSigner = otherSigner)
        assertNull(verifier.verify(token))
    }

    @Test
    fun noneAlgRejected() = runTest {
        val verifier = GoogleIdTokenVerifier(keySource, projectId, clock)
        val claims = JWTClaimsSet.Builder()
            .issuer("https://securetoken.google.com/$projectId")
            .audience(projectId)
            .subject("u1")
            .claim("email", "user@example.com")
            .claim("email_verified", true)
            .issueTime(Date(clock.nowMs()))
            .expirationTime(Date(clock.nowMs() + 3600_000L))
            .build()
        val plainJwt = PlainJWT(claims)
        val token = plainJwt.serialize()
        assertNull(verifier.verify(token))
    }

    @Test
    fun hs256Rejected() = runTest {
        val verifier = GoogleIdTokenVerifier(keySource, projectId, clock)
        val sharedSecret = ByteArray(32) { 1 }
        val macSigner = MACSigner(sharedSecret)
        val claims = JWTClaimsSet.Builder()
            .issuer("https://securetoken.google.com/$projectId")
            .audience(projectId)
            .subject("u1")
            .claim("email", "user@example.com")
            .claim("email_verified", true)
            .issueTime(Date(clock.nowMs()))
            .expirationTime(Date(clock.nowMs() + 3600_000L))
            .build()
        val jwt = SignedJWT(
            JWSHeader.Builder(JWSAlgorithm.HS256).keyID("k1").build(),
            claims
        )
        jwt.sign(macSigner)
        assertNull(verifier.verify(jwt.serialize()))
    }

    @Test
    fun rs512Rejected() = runTest {
        val verifier = GoogleIdTokenVerifier(keySource, projectId, clock)
        val claims = JWTClaimsSet.Builder()
            .issuer("https://securetoken.google.com/$projectId")
            .audience(projectId)
            .subject("u1")
            .claim("email", "user@example.com")
            .claim("email_verified", true)
            .issueTime(Date(clock.nowMs()))
            .expirationTime(Date(clock.nowMs() + 3600_000L))
            .build()
        val jwt = SignedJWT(
            JWSHeader.Builder(JWSAlgorithm.RS512).keyID("k1").build(),
            claims
        )
        jwt.sign(signer)
        assertNull(verifier.verify(jwt.serialize()))
    }
}
