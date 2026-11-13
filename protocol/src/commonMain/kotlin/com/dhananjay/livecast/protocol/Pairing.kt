package com.dhananjay.livecast.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PairingCode(
    val code: String,
    val deviceId: String,
    val hostUid: String,
    val createdAtEpochMs: Long,
    val expiresAtEpochMs: Long,
)

@Serializable
enum class PairingStatus {
    @SerialName("pending") PENDING,
    @SerialName("approved") APPROVED,
    @SerialName("denied") DENIED,
}

@Serializable
data class PairingRequest(
    val id: String,
    val code: String,
    val deviceId: String,
    val clientUid: String,
    val clientName: String,
    val clientEmail: String?,
    val status: PairingStatus,
    val createdAtEpochMs: Long,
    val decidedAtEpochMs: Long? = null,
)

@Serializable
data class Supervisor(
    val uid: String,
    val name: String,
    val email: String?,
    val requestId: String,
    val addedAtEpochMs: Long,
)

object PairingRules {
    const val CODE_LENGTH = 6
    const val CODE_TTL_MS = 600_000L
    const val MAX_CODE_RETRIES = 5

    private val CODE_REGEX = Regex("^[0-9]{6}$")

    fun isValidCode(code: String): Boolean = CODE_REGEX.matches(code)
}
