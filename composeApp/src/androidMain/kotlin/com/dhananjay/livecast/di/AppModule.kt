package com.dhananjay.livecast.di

import com.dhananjay.livecast.webrtc.connection.SignalingClient
import com.dhananjay.livecast.webrtc.session.WebRtcSessionManager
import com.google.firebase.firestore.FirebaseFirestore
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val appModule = module {
    single { FirebaseFirestore.getInstance() }
    singleOf(::SignalingClient)
    singleOf(::WebRtcSessionManagerImpl) { bind<WebRtcSessionManager>()}

    }

