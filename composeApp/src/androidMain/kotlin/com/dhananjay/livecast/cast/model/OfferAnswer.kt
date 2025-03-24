package com.dhananjay.livecast.cast.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName

data class OfferAnswer(
    @DocumentId
    var id: String = "",
    var sdp: String = "",
    @get:PropertyName("is_call_active")
    @set:PropertyName("is_call_active")
    var isCallActive : Boolean = false,
    @get:PropertyName("is_offer")
    @set:PropertyName("is_offer")
    var isOffer: Boolean = false,
    var timestamp: Timestamp = Timestamp.now()
)
