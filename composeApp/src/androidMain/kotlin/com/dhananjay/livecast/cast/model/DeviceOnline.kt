package com.dhananjay.livecast.cast.model

data class DeviceOnline(
    val id: String = "",
    val count: Int = 0,
    val devices: List<String> = emptyList()
)