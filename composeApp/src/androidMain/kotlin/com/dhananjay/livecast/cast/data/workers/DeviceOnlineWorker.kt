package com.dhananjay.livecast.cast.data.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dhananjay.livecast.cast.data.RemoteDataSource
import com.dhananjay.livecast.cast.utils.Constants

class DeviceOnlineWorker(
    private val appContext: Context,
    private val workerParams: WorkerParameters,
    private val remoteDataSource: RemoteDataSource
    ): CoroutineWorker(appContext,workerParams) {

    override suspend fun doWork(): Result {

        val isOnline = inputData.getBoolean(Constants.KEY_IS_ONLINE, false)

        val result = if(isOnline){
            remoteDataSource.addDeviceOnline()
        }else {
            remoteDataSource.removeDeviceOnline()
        }

        return if(result){
            Result.success()
        }else{
            Result.retry()
        }
    }
}