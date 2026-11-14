package com.dhananjay.livecast.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ToggleState {
    @SerialName("on") ON,
    @SerialName("off") OFF,
    UNKNOWN;

    companion object {
        fun fromWire(v: Any?): ToggleState = when (v) {
            "on" -> ON
            "off" -> OFF
            else -> UNKNOWN
        }
    }
}

@Serializable
enum class AppEventType(val wire: String) {
    @SerialName("installed") INSTALLED("installed"),
    @SerialName("removed") REMOVED("removed");

    companion object {
        fun fromWire(v: Any?): AppEventType? = when (v) {
            "installed" -> INSTALLED
            "removed" -> REMOVED
            else -> null
        }
    }
}

@Serializable
data class AppEvent(
    val id: String,
    val type: AppEventType,
    val packageName: String,
    val label: String,
    val atEpochMs: Long,
    val detectedLate: Boolean,
    val groupId: String,
)

object CheckRules {
    const val REPLY_TIMEOUT_MS = 20_000L
    const val FCM_TTL_SECONDS = 30
    const val MIN_INTERVAL_MS = 10_000L
    const val DATA_TYPE = "type"
    const val DATA_CHECK_ID = "checkId"
    const val TYPE_CHECK = "check"
}
