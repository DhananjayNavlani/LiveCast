package com.dhananjay.livecast.agent

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.dhananjay.livecast.R
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

interface AgentNotifications {
    fun showSupervised(byName: String)
    fun showAccessibilityOff()
    fun cancel()
}

class SupervisionRepostReceiver : BroadcastReceiver(), KoinComponent {
    private val agentNotifications by inject<AgentNotifications>()

    override fun onReceive(context: Context, intent: Intent) {
        val name = intent.getStringExtra(EXTRA_BY_NAME)
        if (!name.isNullOrBlank()) {
            agentNotifications.showSupervised(name)
        }
    }

    companion object {
        const val EXTRA_BY_NAME = "com.dhananjay.livecast.agent.extra.BY_NAME"
    }
}

class AndroidAgentNotifications(
    private val context: Context
) : AgentNotifications {

    private val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        ensureChannel()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_AGENT_STATUS,
                "Agent status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows supervision and accessibility status of LiveCast"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun showSupervised(byName: String) {
        val deleteIntent = Intent(context, SupervisionRepostReceiver::class.java).apply {
            putExtra(SupervisionRepostReceiver.EXTRA_BY_NAME, byName)
        }
        val deletePendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_REPOST,
            deleteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_AGENT_STATUS)
            .setSmallIcon(R.drawable.ic_stat_supervised)
            .setContentTitle("Supervised by $byName")
            .setOngoing(true)
            .setDeleteIntent(deletePendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        notificationManager.notify(NOTIFICATION_ID_AGENT_STATUS, notification)
    }

    override fun showAccessibilityOff() {
        val tapIntent = ReEnableIntents.accessibilitySettings(context)
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE_RE_ENABLE,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_AGENT_STATUS)
            .setSmallIcon(R.drawable.ic_stat_supervised)
            .setContentTitle("Accessibility is off — tap to turn on")
            .setOngoing(true)
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(NOTIFICATION_ID_AGENT_STATUS, notification)
    }

    override fun cancel() {
        notificationManager.cancel(NOTIFICATION_ID_AGENT_STATUS)
    }

    companion object {
        const val CHANNEL_ID_AGENT_STATUS = "agent_status"
        const val NOTIFICATION_ID_AGENT_STATUS = 2001
        private const val REQUEST_CODE_REPOST = 101
        private const val REQUEST_CODE_RE_ENABLE = 102
    }
}
