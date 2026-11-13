package com.dhananjay.livecast.protocol

import kotlinx.serialization.Serializable

@Serializable
data class HostDevice(
    val id: String,
    val displayName: String,
    val model: String,
    val online: Boolean = false,
    val lastSeenEpochMs: Long = 0,
)

@Serializable
data class PairingCode(
    val code: String,
    val hostId: String,
    val expiresAtEpochMs: Long,
)
