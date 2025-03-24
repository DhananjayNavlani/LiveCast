package com.dhananjay.livecast.cast.data.services

import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import androidx.compose.ui.geometry.Offset
import com.dhananjay.livecast.cast.utils.Constants

open class AccessibilityService: LiveCastService() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when(intent?.action) {
            Constants.ACTION_SEND_EVENT ->{
                val x = intent.getFloatExtra("x", 0f)
                val y = intent.getFloatExtra("y", 0f)
                val offset = Offset(x, y)
                onTap(offset)
            }
        }
        return START_REDELIVER_INTENT
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        onEvent(event)
    }

    override fun onInterrupt() {
    }
}