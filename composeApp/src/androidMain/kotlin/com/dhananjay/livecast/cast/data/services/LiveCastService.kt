package com.dhananjay.livecast.cast.data.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.compose.ui.geometry.Offset
import com.dhananjay.livecast.cast.data.services.helpers.TouchGestureHelper
import com.dhananjay.livecast.webrtc.connection.SignalingClient
import com.google.firebase.crashlytics.FirebaseCrashlytics
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.OffsetDateTime

abstract class LiveCastService: AccessibilityService(), KoinComponent {
    private val TAG = javaClass.simpleName
    private val signalingClient by inject<SignalingClient>()
    private val crashlytics by inject<FirebaseCrashlytics>()
    private val touchGestureHelper by lazy {
        TouchGestureHelper(this@LiveCastService)
    }


    fun onEvent(event: AccessibilityEvent) {
        //Log all important info from accessibility event
        Log.d(TAG, "onEvent: ${event.eventType} ${event.packageName} ${event.className} ${event.text} ")
    }

    fun onTap(offset: Offset){
        Log.d(TAG, "onTap: offset is $offset")
        // Create a path for the gesture starting at the specified point.
        dispatchGesture(
            GestureDescription.Builder()
                .addStroke(
                    GestureDescription.StrokeDescription(
                        Path().apply {
                            moveTo(offset.x, offset.y)
                        },
                        0L,
                        100L
                    )
                )
                .build(),
            object: GestureResultCallback(){
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    super.onCompleted(gestureDescription)
                    Log.d(TAG, "onCompleted: gesture completed")
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    super.onCancelled(gestureDescription)
                    Log.d(TAG, "onCancelled: gesture cancelled")
                }
            },
            Handler(Looper.getMainLooper())
        )
    }

    override fun onServiceConnected() {
        super.onServiceConnected()

    }

    override fun onUnbind(intent: Intent?): Boolean {
        crashlytics.setCustomKey("onUnbind", "Accessbility service unbound at ${OffsetDateTime.now()}")
        return super.onUnbind(intent)
    }

}