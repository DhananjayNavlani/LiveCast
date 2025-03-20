package com.dhananjay.livecast.cast.data.services

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.dhananjay.livecast.webrtc.connection.SignalingClient
import com.google.firebase.crashlytics.FirebaseCrashlytics
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

abstract class LiveCastService: AccessibilityService(), KoinComponent {
    private val TAG = javaClass.simpleName
    private val signalingClient by inject<SignalingClient>()
    private val crashlytics by inject<FirebaseCrashlytics>()


    fun onEvent(event: AccessibilityEvent) {
        //Log all important info from accessibility event
        Log.d(TAG, "onEvent: ${event.eventType} ${event.packageName} ${event.className} ${event.text} ")

    }

}