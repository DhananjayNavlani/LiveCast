package com.dhananjay.livecast.cast.data.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dhananjay.livecast.cast.utils.Constants
import com.dhananjay.livecast.webrtc.connection.SignalingClient

class DeviceOnlineWorker(
    private val appContext: Context,
    private val workerParams: WorkerParameters,
    private val signalingClient: SignalingClient
    ): CoroutineWorker(appContext,workerParams) {

    override suspend fun doWork(): Result {

        val isOnline = inputData.getBoolean(Constants.KEY_IS_ONLINE, false)

        val result = if(isOnline){
            signalingClient.addDeviceOnline()
        }else {
            signalingClient.removeDeviceOnline()
        }

        return if(result){
            Result.success()
        }else{
            Result.failure()
        }
    }
}