package com.dhananjay.livecast

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.dhananjay.livecast.webrtc.connection.SignalingClient
import com.dhananjay.livecast.webrtc.peer.StreamPeerConnectionFactory
import com.dhananjay.livecast.webrtc.session.WebRtcSessionManager
import com.dhananjay.livecast.webrtc.session.WebRtcSessionManagerImpl

class MainActivity : ComponentActivity() {
    private val sessionManager: WebRtcSessionManager = WebRtcSessionManagerImpl(
        this,
        signalingClient = SignalingClient(),
        peerConnectionFactory = StreamPeerConnectionFactory(this)
    )
    private val captureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()){
        result ->
        if(result.resultCode != RESULT_OK || result.data == null) return@registerForActivityResult
        sessionManager.handleScreenSharing(result.data!!)

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            App()
            
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}