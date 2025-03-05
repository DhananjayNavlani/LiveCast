package com.dhananjay.livecast.cast.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ServerTimestamp
import org.webrtc.SessionDescription

data class OfferAnswer(
    @DocumentId
    val id: String = "",
    val sdp: String,
    val isOffer: Boolean = false,
    val timestamp: FieldValue = FieldValue.serverTimestamp()
)
