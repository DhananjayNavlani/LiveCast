package com.dhananjay.livecast

import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dhananjay.livecast.cast.stage.StageScreen
import com.dhananjay.livecast.cast.video.ScreenCastScreen
import com.dhananjay.livecast.webrtc.connection.SignalingClient
import com.dhananjay.livecast.webrtc.peer.StreamPeerConnectionFactory
import com.dhananjay.livecast.webrtc.session.LocalWebRtcSessionManager
import com.dhananjay.livecast.webrtc.session.WebRtcSessionManager
import com.dhananjay.livecast.webrtc.session.WebRtcSessionManagerImpl
import org.koin.java.KoinJavaComponent.inject

class MainActivity : ComponentActivity() {
    private val sessionManager: WebRtcSessionManager by inject()
    private val captureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()){
        result ->
        if(result.resultCode != RESULT_OK || result.data == null) return@registerForActivityResult
        sessionManager.handleScreenSharing(result.data!!)

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val manager = getSystemService(MediaProjectionManager::class.java)
        setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalWebRtcSessionManager provides sessionManager) {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colors.background) {
                        var onCallScreen by remember { mutableStateOf(false) }
                        val state by sessionManager.signalingClient.sessionStateFlow.collectAsStateWithLifecycle()
                        if (!onCallScreen) {
                            StageScreen(state = state,{
                                onCallScreen = true
                            })
                        } else {
                            ScreenCastScreen{
                                captureLauncher.launch(manager.createScreenCaptureIntent())
                            }
                        }
                    }
                }

            }

        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}