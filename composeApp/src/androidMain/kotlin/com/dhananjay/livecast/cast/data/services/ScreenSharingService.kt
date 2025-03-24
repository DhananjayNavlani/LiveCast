package com.dhananjay.livecast.cast.data.services

import android.app.Service
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.dhananjay.livecast.R
import com.dhananjay.livecast.cast.utils.Constants
import com.dhananjay.livecast.cast.utils.NotificationHelper
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ScreenSharingService : Service(), KoinComponent {
    private val notificationHelper by inject<NotificationHelper>()
    override fun onCreate() {
        super.onCreate()
        val id = notificationHelper.getUniqueId()
        val notification = notificationHelper.createNotification(
            channelId = Constants.CHANNEL_ID_APP,
            title = getString(R.string.app_name),
            text = getString(R.string.screen_sharing),
            priority = NotificationCompat.PRIORITY_HIGH,
            groupKey = Constants.GROUP_KEY_APP,
            autoCancel = true
        ).build()
        startForeground(id,notification)

    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        intent?.let {
            when (it.action) {
                Constants.ACTION_START_SCREEN_SHARING -> {

                    // Start screen sharing
                }
                Constants.ACTION_STOP_SCREEN_SHARING -> {
                    // Stop screen sharing
                    stop()
                }
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun stop(){
        stopSelf()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }
    override fun onBind(intent: Intent) = null
}