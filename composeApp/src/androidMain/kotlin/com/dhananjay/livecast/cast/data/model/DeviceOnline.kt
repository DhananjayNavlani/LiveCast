package com.dhananjay.livecast.cast.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

data class DeviceOnline(
    @DocumentId
    val id: String = "",
    val count: Int = 0,
    val names: List<String> = emptyList(),
    val devices: List<Int> = emptyList(),
    @ServerTimestamp
    @PropertyName("last_updated")
    val lastUpdated: Timestamp? = null
)