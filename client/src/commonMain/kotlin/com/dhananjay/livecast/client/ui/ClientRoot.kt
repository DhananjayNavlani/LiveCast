package com.dhananjay.livecast.client.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dhananjay.livecast.core.LiveCastResult
import com.dhananjay.livecast.protocol.DeviceState
import com.dhananjay.livecast.transport.SignalMessage

@Composable
fun ClientRoot(
    deviceState: DeviceState = DeviceState(online = true),
    signalMessage: SignalMessage = SignalMessage.Bye,
    result: LiveCastResult<String> = LiveCastResult.Success("connected"),
) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "LiveCast Client",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = "Shared wiring: DeviceState(online=${deviceState.online}), SignalMessage=${signalMessage::class.simpleName}, Result=$result",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}
