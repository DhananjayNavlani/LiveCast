package com.dhananjay.livecast.protocol

import kotlinx.serialization.Serializable

@Serializable sealed interface HostCommand { @Serializable data object Ping : HostCommand }
@Serializable sealed interface HostEvent { @Serializable data object Pong : HostEvent }
@Serializable data class DeviceState(val online: Boolean = false)
