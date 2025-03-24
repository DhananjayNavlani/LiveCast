package com.dhananjay.livecast.cast.data.services

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.compose.ui.geometry.Offset
import com.dhananjay.livecast.MainActivity
import com.dhananjay.livecast.cast.data.RemoteDataSource
import com.dhananjay.livecast.cast.data.services.helpers.TouchGestureHelper
import com.dhananjay.livecast.cast.model.DeviceConfig
import com.dhananjay.livecast.webrtc.connection.SignalingClient
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


    fun onEvent(event: AccessibilityEvent) {
        //Log all important info from accessibility event
        Log.d(
            TAG,
            "onEvent: ${event.eventType} ${event.packageName} ${event.className} ${event.text} "
        )

    }

    fun onTap(offset: Offset) {
        TouchGestureHelper.tap(this, offset.x, offset.y,
            {
                Log.d(TAG, "onTap: tap result is $it")
            })

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
                    val cmpName = ComponentName(this@LiveCastService, "${packageName}.LauncherAlias")
                    Log.d(
                        TAG,
                        "startObservers: $cmpName \n ${LauncherAlias::class.java.canonicalName} \n ${LauncherAlias::class.java.name} \n ${LauncherAlias::class.java.simpleName} \n ${LauncherAlias::class.java.typeName} \n ${LauncherAlias::class.java.toString()}")

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
    }

    override fun onUnbind(intent: Intent?): Boolean {
        crashlytics.setCustomKey(
            "onUnbind",
            "Accessbility service unbound at ${OffsetDateTime.now()}"
        )
        return super.onUnbind(intent)
    }

}

class LauncherAlias