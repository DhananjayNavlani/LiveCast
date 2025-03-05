package com.dhananjay.livecast.cast.model

import com.dhananjay.livecast.webrtc.session.ICE_SEPARATOR
import com.google.firebase.firestore.DocumentId

data class Ice(
    @DocumentId
    val id: String = "",
    val candidate: String,
    val sdpMLineIndex: Int,
    val sdpMid: String
){
    override fun toString(): String {
        return "$sdpMid$ICE_SEPARATOR$sdpMLineIndex$ICE_SEPARATOR$candidate"
    }
}
