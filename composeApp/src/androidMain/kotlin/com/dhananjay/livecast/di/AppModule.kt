package com.dhananjay.livecast.di

import com.dhananjay.livecast.cast.data.services.helpers.TouchGestureHelper
import androidx.work.WorkManager
import com.dhananjay.livecast.MainViewModel
import com.dhananjay.livecast.cast.data.workers.DeviceOnlineWorker
import com.dhananjay.livecast.cast.utils.NotificationHelper
import com.dhananjay.livecast.webrtc.connection.SignalingClient
import com.dhananjay.livecast.webrtc.peer.StreamPeerConnectionFactory
import com.dhananjay.livecast.webrtc.session.WebRtcSessionManager
import com.dhananjay.livecast.webrtc.session.WebRtcSessionManagerImpl
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.firestore.FirebaseFirestore
import org.koin.androidx.workmanager.dsl.worker
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import com.dhananjay.livecast.cast.data.RemoteDataSource
val appModule = module {
    single { FirebaseFirestore.getInstance() }
    single { FirebaseCrashlytics.getInstance() }
    single { FirebaseAnalytics.getInstance(get()) }
    singleOf(::SignalingClient)
    singleOf(::StreamPeerConnectionFactory)
    factoryOf(::WebRtcSessionManagerImpl) {
        bind<WebRtcSessionManager>()
    }
    singleOf(::NotificationHelper)
    singleOf(::RemoteDataSource)
    worker { DeviceOnlineWorker(get(),get(),get()) }
    viewModel { MainViewModel(get()) }
    single { WorkManager.getInstance(get()) }
}

