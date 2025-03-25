package com.dhananjay.livecast.cast.data.model

import com.dhananjay.livecast.webrtc.session.ICE_SEPARATOR
import com.google.firebase.firestore.DocumentId

data class Ice(
    @DocumentId
    var id: String = "",
    var candidate: String = "",
    var sdpMLineIndex: Int = 0,
    var sdpMid: String = ""
){
    override fun toString(): String {
        return "$sdpMid$ICE_SEPARATOR$sdpMLineIndex$ICE_SEPARATOR$candidate"
    }
}
