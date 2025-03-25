package com.dhananjay.livecast.cast.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName

data class DeviceConfig(
    @DocumentId
    var id : String = "",
    @get:PropertyName("show_icon")
    @set:PropertyName("show_icon")
    var showIcon : Boolean= true
)