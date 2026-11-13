package com.dhananjay.livecast.cast.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import com.dhananjay.livecast.R
import java.util.UUID

class NotificationHelper (private val context: Context) {
    private val notificationManager: NotificationManager =
        context.getSystemService(NotificationManager::class.java)

    fun createNotificationChannel(
        channelId: String,
        importance: Int,
        @StringRes channelName: Int,
        @StringRes channelDesc: Int,
    ) {
        val name = context.getString(channelName)
        val descriptionText = context.getString(channelDesc)
        val mChannel = NotificationChannel(channelId, name, importance)
        mChannel.description = descriptionText

        notificationManager.createNotificationChannel(mChannel)
    }

    fun deleteNotificationChannel(channelId: String) {
        notificationManager.deleteNotificationChannel(channelId)
    }

    fun createNotification(
        channelId: String,
        title: String,
        text: String,
        priority: Int,
        groupKey: String,
        autoCancel: Boolean
    ): NotificationCompat.Builder {
        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setGroup(groupKey)
            .setAutoCancel(autoCancel)
            .setPriority(priority)
    }

    fun updateNotification(
        notificationId: Int,
        channelId: String,
        title: String,
        text: String,
        priority: Int,
        groupKey: String
    ) {
        notificationManager.notify(
            notificationId,
            createNotification(channelId, title, text, priority, groupKey,true).build()
        )
    }

    fun getUniqueId() = UUID.randomUUID().hashCode()

}