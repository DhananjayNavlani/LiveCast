package com.dhananjay.livecast.auth

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.util.Base64

private fun jwtPayload(idToken: String): JsonObject? {
    val parts = idToken.split('.')
    if (parts.size != 3) return null

    val payload = parts[1]
    val padded = when (payload.length % 4) {
        2 -> "$payload=="
        3 -> "$payload="
        0 -> payload
        else -> return null
    }

    val jsonString = try {
        Base64.getUrlDecoder().decode(padded).toString(Charsets.UTF_8)
    } catch (e: Exception) {
        return null
    }

    return try {
        Json.parseToJsonElement(jsonString).jsonObject
    } catch (e: Exception) {
        null
    }
}

internal fun jwtExpiryEpochSeconds(idToken: String): Long? {
    return try {
        jwtPayload(idToken)?.get("exp")?.jsonPrimitive?.longOrNull
    } catch (e: Exception) {
        null
    }
}

internal fun jwtEmailVerified(idToken: String): Boolean {
    return try {
        jwtPayload(idToken)?.get("email_verified")?.jsonPrimitive?.booleanOrNull == true
    } catch (e: Exception) {
        false
    }
}

internal fun needsRefresh(idToken: String, nowEpochSeconds: Long, skewSeconds: Long = 300): Boolean {
    val expiry = jwtExpiryEpochSeconds(idToken) ?: return true
    return nowEpochSeconds >= (expiry - skewSeconds)
}
