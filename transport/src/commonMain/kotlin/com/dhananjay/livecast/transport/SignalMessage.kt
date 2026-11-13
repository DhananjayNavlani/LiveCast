package com.dhananjay.livecast.transport

import kotlinx.serialization.Serializable

@Serializable
sealed interface SignalMessage {
    @Serializable
    data class Offer(val sdp: String) : SignalMessage

    @Serializable
    data class Answer(val sdp: String) : SignalMessage

    @Serializable
    data class Ice(val candidate: String) : SignalMessage

    @Serializable
    data object Bye : SignalMessage
}
