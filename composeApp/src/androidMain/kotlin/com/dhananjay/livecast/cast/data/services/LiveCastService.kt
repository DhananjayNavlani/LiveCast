package com.dhananjay.livecast.cast.data.services

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.dhananjay.livecast.cast.data.RemoteDataSource
import com.dhananjay.livecast.cast.data.services.helpers.TouchGestureHelper
import com.dhananjay.livecast.cast.model.DeviceConfig
import com.dhananjay.livecast.cast.ui.video.GestureType
import com.dhananjay.livecast.webrtc.connection.SignalingClient
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
    }


    override fun onServiceConnected() {
        super.onServiceConnected()

        startObservers()

    }

    private fun startObservers() {
        serviceScope.launch {
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
                when(it.first){
                    GestureType.TAP -> {
                        TouchGestureHelper.tap(context, it.second.x, it.second.y,)
                    }
                    GestureType.DOUBLE_TAP -> TouchGestureHelper.doubleTap(context, it.second.x, it.second.y)
                    GestureType.LONG_PRESS -> TouchGestureHelper.longPress(context, it.second.x, it.second.y)
                    GestureType.PINCH -> TouchGestureHelper.pinch(context, it.second.x, it.second.y, it.third?.x ?: 0f, it.third?.y ?: 0f)
                    GestureType.SWIPE_UP -> TouchGestureHelper.swipe(context,it.second.x,it.second.y,it.third?.x ?: it.second.x,it.third?.y ?: 0f)
                    GestureType.SWIPE_DOWN -> TouchGestureHelper.swipe(context, it.second.x, it.second.y, it.third?.x ?: it.second.x, it.third?.y ?: 500f)
                    GestureType.SWIPE_LEFT -> TouchGestureHelper.swipe(context, it.second.x, it.second.y, it.third?.x ?: 0f, it.third?.y ?: it.second.y)
                    GestureType.SWIPE_RIGHT -> TouchGestureHelper.swipe(context,it.second.x, it.second.y, it.third?.x ?: 500f, it.third?.y ?: it.second.y)
                    else -> {
                        Log.d(TAG, "onServiceConnected: Unknown gesture type")
                    }
                }
            }
        }
    }



    override fun onUnbind(intent: Intent?): Boolean {
        crashlytics.setCustomKey(
            "onUnbind",
            "Accessbility service unbound at ${OffsetDateTime.now()}"
        )
        return super.onUnbind(intent)
    }

}
