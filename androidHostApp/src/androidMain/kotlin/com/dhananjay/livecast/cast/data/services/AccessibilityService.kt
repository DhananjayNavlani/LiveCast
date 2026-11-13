package com.dhananjay.livecast.cast.data.services

import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.dhananjay.livecast.cast.utils.Constants

open class AccessibilityService: LiveCastService() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when(intent?.action) {
            Constants.ACTION_SEND_EVENT ->{
            }

            Constants.ACTION_STOP_ACCESSILIBITY_SERVICE -> {
                disableSelf()
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