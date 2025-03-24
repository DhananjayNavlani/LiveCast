package com.dhananjay.livecast

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dhananjay.livecast.cast.presentation.stage.StageScreen
import com.dhananjay.livecast.cast.presentation.video.VideoScreenActivity
import com.dhananjay.livecast.cast.utils.Constants
import com.dhananjay.livecast.webrtc.connection.SignalingClient
import org.koin.android.ext.android.inject
import org.koin.androidx.compose.KoinAndroidContext
import org.koin.compose.koinInject
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    private val viewModel by inject<MainViewModel>()

    private val TAG = javaClass.simpleName

    override fun onStart() {
        super.onStart()
        viewModel.addDeviceOnline()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                KoinAndroidContext {
                    Surface(
                        modifier = Modifier.fillMaxSize(), color = Color(Random.nextFloat(), Random.nextFloat(), Random.nextFloat())
                    ) {
                        val state by koinInject<SignalingClient>().devicesOnline.collectAsStateWithLifecycle(null)
                        StageScreen(
                            state = state,
                            onStart = {
                                startActivity(Intent(this, VideoScreenActivity::class.java).apply {
                                    putExtra(Constants.EXTRA_IS_SUBSCRIBER, true)
                                })
                            }, onAnswer = {
                                startActivity(Intent(this, VideoScreenActivity::class.java).apply {
                                    putExtra(Constants.EXTRA_IS_SUBSCRIBER, false)
                                })
                            })
                    }

                }
            }

        }
    }

    override fun onStop() {
        super.onStop()
        viewModel.removeDeviceOnline()
    }

}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}