package com.dhananjay.livecast.cast.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
@IgnoreExtraProperties
data class LiveCastUser(
    // User identity
    var uid: String = "",
    var name: String = "",
    var email: String? = null,
    @PropertyName("phone_number")
    var phoneNumber: String? = null,
    @PropertyName("photo_url")
    var photoUrl: String? = null,

    // User role
    @PropertyName("is_viewer")
    var isViewer: Boolean = false,
    @PropertyName("is_broadcaster")
    var isBroadcaster: Boolean = false,

    // Online status
    @PropertyName("is_online")
    var isOnline: Boolean = false,
    @Transient
    @ServerTimestamp
    @PropertyName("last_seen")
    var lastSeen: Timestamp? = null,

    // Platform info
    @PropertyName("platform")
    var platform: String = "android",  // "android", "ios", "web", "desktop"

    // Legacy screen dimensions (kept for backward compatibility)
    @PropertyName("width_pixels")
    var widthPixels: Int = 0,
    @PropertyName("height_pixels")
    var heightPixels: Int = 0,

    // Device info (nested object)
    @Transient
    @PropertyName("device")
    var device: DeviceInfo? = null,
)