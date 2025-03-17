package com.dhananjay.livecast.cast.stage

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.dhananjay.livecast.cast.model.DeviceOnline
import com.dhananjay.livecast.webrtc.session.LocalWebRtcSessionManager


@Composable
fun StageScreen(
    state: DeviceOnline?,
    onJoinCall: () -> Unit,
    modifier: Modifier = Modifier) {

    val sessionManager = LocalWebRtcSessionManager.current
    Box(modifier = Modifier.fillMaxSize()) {
        var enabledCall by remember { mutableStateOf(false) }

/*        val text = when (state) {
            WebRTCSessionState.Offline -> {
                enabledCall = false
                stringResource(id = R.string.button_start_session)
            }
            WebRTCSessionState.Impossible -> {
                enabledCall = false
                stringResource(id = R.string.session_impossible)
            }
            WebRTCSessionState.Ready -> {
                enabledCall = true
                stringResource(id = R.string.session_ready)
            }
            WebRTCSessionState.Creating -> {
                enabledCall = true
                stringResource(id = R.string.session_creating)
            }
            WebRTCSessionState.Active -> {
                enabledCall = false
                stringResource(id = R.string.session_active)
            }
        }

        Button(
            modifier = Modifier.align(Alignment.Center),
            enabled = enabledCall,
            onClick = { onJoinCall.invoke() }
        ) {
            Text(
                text = text,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )
        }*/

        Column(
            modifier = modifier.align(Alignment.TopCenter)
        ) {
            Text("Count:${state?.count ?: 0}")
            if(state?.count != null && state.count > 0) {
                LazyColumn {
                    items(state.names) { device ->
                        Text(device)
                    }
                }

            }
        }
        Button(onClick = {
            onJoinCall()
        },
            modifier = Modifier.align(Alignment.Center)) {
            Text(text = "Start Session")
        }
    }

}