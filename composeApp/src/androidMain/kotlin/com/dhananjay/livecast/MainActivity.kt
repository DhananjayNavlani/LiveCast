package com.dhananjay.livecast

import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.dhananjay.livecast.cast.data.services.ScreenSharingService
import com.dhananjay.livecast.cast.data.workers.DeviceOnlineWorker
import com.dhananjay.livecast.cast.presentation.stage.StageScreen
import com.dhananjay.livecast.cast.presentation.video.ScreenCastScreen
import com.dhananjay.livecast.cast.utils.Constants
import com.dhananjay.livecast.webrtc.session.LocalWebRtcSessionManager
import com.dhananjay.livecast.webrtc.session.WebRtcSessionManager
import org.koin.android.ext.android.inject
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    private val sessionManager: WebRtcSessionManager by inject()
    private val workManager: WorkManager by inject()
    private val viewModel by inject<MainViewModel>()
    private val oneTimeWorkReq = OneTimeWorkRequestBuilder<DeviceOnlineWorker>()
        .setConstraints(
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        )
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)

    private val TAG = javaClass.simpleName
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
            viewModel.updateOnCallScreen(true)

        }

    override fun onStart() {
        super.onStart()
//        workManager.enqueueUniqueWork(
//            Constants.WORK_DEVICE_ONLINE, ExistingWorkPolicy.REPLACE, oneTimeWorkReq.setInputData(workDataOf(Constants.KEY_IS_ONLINE to true)).build()
//        )
        viewModel.addDeviceOnline()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val manager = getSystemService(MediaProjectionManager::class.java)
        setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalWebRtcSessionManager provides sessionManager) {
                    Surface(
                        modifier = Modifier.fillMaxSize(), color = MaterialTheme.colors.background
                    ) {
                        val onCallScreen by viewModel.onCallScreen.collectAsStateWithLifecycle()
                        val isSubscriber by viewModel.isSubscriber.collectAsStateWithLifecycle()
                        val state by sessionManager.signalingClient.devicesOnline.collectAsStateWithLifecycle(
                            null
                        )
                        if (!onCallScreen) {
                            StageScreen(state = state, onStart = {
                                viewModel.updateIsSubscriber(true)
                                viewModel.updateOnCallScreen(true)
                            }, onAnswer = {
                                captureLauncher.launch(manager.createScreenCaptureIntent())
                                viewModel.updateIsSubscriber(false)
                            })
                        } else {
                            ScreenCastScreen(
                                isSubscriber,
                                onEnd = {
                                    viewModel.updateOnCallScreen(false)
                                }
                            )
                        }
                    }
                }

            }

        }
    }

    override fun onStop() {
        super.onStop()
//        workManager.enqueueUniqueWork(
//            Constants.WORK_DEVICE_ONLINE, ExistingWorkPolicy.REPLACE,
//            oneTimeWorkReq
//                .setInputData(workDataOf(Constants.KEY_IS_ONLINE to false))
//                .build()
//        )
        viewModel.removeDeviceOnline()
    }

    override fun onDestroy() {
        super.onDestroy()
        sessionManager.disconnect(viewModel.isSubscriber.value)
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}