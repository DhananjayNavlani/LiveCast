package com.dhananjay.livecast.cast.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName

@IgnoreExtraProperties
data class OfferAnswer(
    @DocumentId
    var id: String = "",
    var sdp: String = "",
    @PropertyName("is_call_active")
    var isCallActive : Boolean = false,
    @PropertyName("is_offer")
    var isOffer: Boolean = false,
    @PropertyName("viewer_user_id")
    var viewerUserId : String? = null,
    var timestamp: Timestamp = Timestamp.now()
)
