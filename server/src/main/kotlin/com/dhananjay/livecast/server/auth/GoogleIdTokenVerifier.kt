package com.dhananjay.livecast.server.auth

import com.dhananjay.livecast.server.Clock
import com.dhananjay.livecast.server.IdTokenVerifier
import com.dhananjay.livecast.server.PublicKeySource
import com.dhananjay.livecast.server.VerifiedCaller
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jwt.SignedJWT
import kotlin.coroutines.cancellation.CancellationException

class GoogleIdTokenVerifier(
    private val keys: PublicKeySource,
    private val projectId: String,
    private val clock: Clock
) : IdTokenVerifier {

    override suspend fun verify(idToken: String): VerifiedCaller? {
        val jwt = try {
            SignedJWT.parse(idToken)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return null
        }

        if (jwt.header.algorithm != JWSAlgorithm.RS256) return null

        val kid = jwt.header.keyID ?: return null
        val publicKeyMap = keys.keys()
        val publicKey = publicKeyMap[kid] ?: return null

        val verified = try {
            jwt.verify(RSASSAVerifier(publicKey))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
        if (!verified) return null

        val claims = jwt.jwtClaimsSet

        val expectedIssuer = "https://securetoken.google.com/$projectId"
        if (claims.issuer != expectedIssuer) return null

        val audList = claims.audience
        if (audList != listOf(projectId)) return null

        val sub = claims.subject
        if (sub.isNullOrEmpty()) return null

        val email = claims.getStringClaim("email")
        if (email.isNullOrBlank()) return null

        val emailVerified = claims.getClaim("email_verified")
        if (emailVerified != true) return null

        val nowSec = clock.nowMs() / 1000
        val exp = claims.expirationTime?.time?.div(1000) ?: return null
        if (exp <= nowSec - 60) return null

        val iat = claims.issueTime?.time?.div(1000) ?: return null
        if (iat > nowSec + 60) return null

        return VerifiedCaller(uid = sub, email = email)
    }
}
