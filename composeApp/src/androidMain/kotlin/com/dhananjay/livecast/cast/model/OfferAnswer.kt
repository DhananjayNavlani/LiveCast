package com.dhananjay.livecast.cast.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

data class OfferAnswer(
    @DocumentId
    var id: String = "",
    var sdp: String = "",
    var isOffer: Boolean = false,
    var timestamp: Timestamp = Timestamp.now()
)
