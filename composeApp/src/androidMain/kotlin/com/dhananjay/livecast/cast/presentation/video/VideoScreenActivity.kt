package com.dhananjay.livecast.cast.presentation.video

import android.content.Intent
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
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
                                    isSubscriber
                                )
                            } else{
                                Greeting(name = "User")
                            }
                        }

                    }
                }
            }
        }
    }

}
@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MaterialTheme {
        Greeting("Android")
    }
}