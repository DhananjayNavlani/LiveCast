package com.dhananjay.livecast.di

import androidx.work.WorkManager
import com.dhananjay.livecast.cast.data.workers.DeviceOnlineWorker
import com.dhananjay.livecast.webrtc.connection.SignalingClient
import com.dhananjay.livecast.webrtc.peer.StreamPeerConnectionFactory
import com.dhananjay.livecast.webrtc.session.WebRtcSessionManager
import com.dhananjay.livecast.webrtc.session.WebRtcSessionManagerImpl
import com.google.firebase.firestore.FirebaseFirestore
import org.koin.androidx.workmanager.dsl.worker
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val appModule = module {
    single { FirebaseFirestore.getInstance() }
    singleOf(::SignalingClient)
    singleOf(::StreamPeerConnectionFactory)
    singleOf(::WebRtcSessionManagerImpl) {
        bind<WebRtcSessionManager>()
    }
    worker { DeviceOnlineWorker(get(), get(), get()) }
    single { WorkManager.getInstance(get()) }
}

