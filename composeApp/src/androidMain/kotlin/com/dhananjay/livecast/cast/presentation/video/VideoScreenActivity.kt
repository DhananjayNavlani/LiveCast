package com.dhananjay.livecast.cast.presentation.video

import android.content.Intent
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dhananjay.livecast.cast.data.services.ScreenSharingService
import com.dhananjay.livecast.cast.utils.Constants
import com.dhananjay.livecast.webrtc.session.LocalWebRtcSessionManager
import com.dhananjay.livecast.webrtc.session.WebRtcSessionManager
import org.koin.android.ext.android.inject
import org.koin.androidx.compose.KoinAndroidContext

class VideoScreenActivity : ComponentActivity() {
    private val sessionManager: WebRtcSessionManager by inject()
    private val TAG = javaClass.simpleName
    private var isPermissionGranted by mutableStateOf(false)
    private val captureLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            Log.d(TAG, "The result is ${result.resultCode} && ${result.data?.action} ")
            if (result.resultCode != RESULT_OK || result.data == null) return@registerForActivityResult
            sessionManager.handleScreenSharing(result.data!!)
            Intent(this, ScreenSharingService::class.java).apply {
                action = Constants.ACTION_START_SCREEN_SHARING
            }.also {
                startService(it)
            }
            isPermissionGranted = true

        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val manager = getSystemService(MediaProjectionManager::class.java)

        val isSubscriber = intent.getBooleanExtra(Constants.EXTRA_IS_SUBSCRIBER, false)
        if (!isSubscriber) {
            isPermissionGranted = false
            captureLauncher.launch(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
                } else manager.createScreenCaptureIntent()
            )
        } else {
            isPermissionGranted = true
        }
        setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalWebRtcSessionManager provides sessionManager) {
                    KoinAndroidContext {
                        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                            if(isPermissionGranted){
                                ScreenCastScreen(
                                    isSubscriber,
                                    Modifier.padding(innerPadding)
                                )
                            } else{
                                WaitingScreen(modifier = Modifier.padding(innerPadding))
                            }
                        }

                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sessionManager.disconnect()
    }

}
@Composable
fun WaitingScreen(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center){
        Text(
            text = "Waiting for permission",
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MaterialTheme {
        WaitingScreen()
    }
}