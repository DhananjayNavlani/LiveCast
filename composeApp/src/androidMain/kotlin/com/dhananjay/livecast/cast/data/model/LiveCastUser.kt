package com.dhananjay.livecast.cast.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import kotlinx.serialization.Serializable

@Serializable
@IgnoreExtraProperties
data class LiveCastUser(
    @DocumentId
    var uid: String = "",
    var name: String = "",
    var email: String? = null,
    @PropertyName("phone_number")
    var phoneNumber: String? = null,
    @PropertyName("photo_url")
    var photoUrl: String? = null,
    @PropertyName("is_viewer")
    var isViewer: Boolean = false,
    @PropertyName("is_online")
    var isOnline: Boolean = false,
    @PropertyName("width_pixels")
    var widthPixels: Int = 0,
    @PropertyName("height_pixels")
    var heightPixels: Int = 0,
)