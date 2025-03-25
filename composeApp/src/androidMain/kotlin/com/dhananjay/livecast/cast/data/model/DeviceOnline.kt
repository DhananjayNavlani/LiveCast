package com.dhananjay.livecast.cast.data.model

import com.google.firebase.firestore.DocumentId

data class DeviceOnline(
    @DocumentId
    val id: String = "",
    val count: Int = 0,
    val names: List<String> = emptyList(),
    val devices: List<Int> = emptyList()
)