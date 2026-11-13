package com.dhananjay.livecast.di

import android.os.Build
import android.provider.Settings
import androidx.work.WorkManager
import com.dhananjay.livecast.MainViewModel
import com.dhananjay.livecast.analytics.Analytics
import com.dhananjay.livecast.analytics.createAnalytics
import com.dhananjay.livecast.cast.data.PermissionManager
import com.dhananjay.livecast.cast.data.RemoteDataSource
import com.dhananjay.livecast.cast.data.repositories.AuthRepository
import com.dhananjay.livecast.cast.data.repositories.PreferencesRepository
import com.dhananjay.livecast.cast.data.workers.DeviceOnlineWorker
import com.dhananjay.livecast.cast.utils.NotificationHelper
import com.dhananjay.livecast.pairing.FirestoreHostPairingRepository
import com.dhananjay.livecast.pairing.HostPairingRepository
import com.dhananjay.livecast.pairing.HostPairingViewModel
import com.dhananjay.livecast.protocol.HostDevice
import com.dhananjay.livecast.webrtc.connection.SignalingClient
import com.dhananjay.livecast.webrtc.peer.StreamPeerConnectionFactory
import com.dhananjay.livecast.webrtc.session.WebRtcSessionManager
import com.dhananjay.livecast.webrtc.session.WebRtcSessionManagerImpl
import com.firebase.ui.auth.AuthUI
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.firestore.FirebaseFirestore
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.dsl.worker
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { FirebaseFirestore.getInstance() }
    single <FirebaseCrashlytics>{ FirebaseCrashlytics.getInstance() }
    single { FirebaseAnalytics.getInstance(get()) }
    single<Analytics> { createAnalytics() }
    single { FirebaseAuth.getInstance() }
    single { AuthUI.getInstance() }
    singleOf(::SignalingClient)
    singleOf(::StreamPeerConnectionFactory)
    factoryOf(::WebRtcSessionManagerImpl) {
        bind<WebRtcSessionManager>()
    }
    singleOf(::NotificationHelper)
    single { RemoteDataSource(get(), get(), get(), get()) }
    singleOf(::AuthRepository)
    singleOf(::PreferencesRepository)
    singleOf(::PermissionManager)
    worker { DeviceOnlineWorker(get(),get(),get()) }
    viewModel { MainViewModel(get(),get(),get()) }
    single<(String) -> HostDevice> {
        val context = androidContext()
        val provider: (String) -> HostDevice = { deviceId ->
            val rawName = runCatching {
                Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
            }.getOrNull()
            val displayName = if (!rawName.isNullOrBlank()) rawName else Build.MODEL
            HostDevice(
                id = deviceId,
                displayName = displayName,
                model = Build.MODEL
            )
        }
        provider
    }
    single<HostPairingRepository> { FirestoreHostPairingRepository(get()) }
    viewModel { HostPairingViewModel(get(), get(), deviceProvider = get()) }
    single { WorkManager.getInstance(get()) }
}

