package com.dhananjay.livecast.client.ui

import androidx.compose.runtime.Composable
import com.dhananjay.livecast.core.LiveCastResult
import com.dhananjay.livecast.protocol.DeviceState
import com.dhananjay.livecast.transport.SignalMessage

@Composable
fun ClientRoot(
    deviceState: DeviceState = DeviceState(online = true),
    signalMessage: SignalMessage = SignalMessage.Bye,
    result: LiveCastResult<String> = LiveCastResult.Success("connected"),
) {
    ClientApp()
}
