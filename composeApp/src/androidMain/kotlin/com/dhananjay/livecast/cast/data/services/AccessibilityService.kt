package com.dhananjay.livecast.cast.data.services

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

open class AccessibilityService: LiveCastService() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        return START_REDELIVER_INTENT
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        onEvent(event)
    }

    override fun onInterrupt() {
    }
}