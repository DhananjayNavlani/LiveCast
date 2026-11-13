package com.dhananjay.livecast.di

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
import com.dhananjay.livecast.group.AndroidHostDeviceInfoProvider
import com.dhananjay.livecast.group.DeviceIdentity
import com.dhananjay.livecast.group.DeviceRegistry
import com.dhananjay.livecast.group.EnrollmentDirectory
import com.dhananjay.livecast.group.EnrollmentViewModel
import com.dhananjay.livecast.group.FirebaseDeviceIdentity
import com.dhananjay.livecast.group.FirestoreDeviceRegistry
import com.dhananjay.livecast.group.FirestoreEnrollmentDirectory
import com.dhananjay.livecast.group.FirestoreGroupMembersDirectory
import com.dhananjay.livecast.group.FirestorePresenceReporter
import com.dhananjay.livecast.group.GroupMembersDirectory
import com.dhananjay.livecast.group.HostDeviceInfoProvider
import com.dhananjay.livecast.group.HostPresenceViewModel
import com.dhananjay.livecast.group.PresenceHeartbeat
import com.dhananjay.livecast.group.PresenceReporter
import com.dhananjay.livecast.group.SupervisedByViewModel
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

    single<DeviceIdentity> { FirebaseDeviceIdentity(get()) }
    single<HostDeviceInfoProvider> { AndroidHostDeviceInfoProvider(androidContext()) }
    single<DeviceRegistry> { FirestoreDeviceRegistry(get()) }
    single<PresenceReporter> { FirestorePresenceReporter(get()) }
    single { PresenceHeartbeat(get()) }
    single<EnrollmentDirectory> { FirestoreEnrollmentDirectory(get()) }
    single<GroupMembersDirectory> { FirestoreGroupMembersDirectory(get()) }
    viewModel { HostPresenceViewModel(get(), get()) }
    viewModel { EnrollmentViewModel(get(), get(), get(), get(), get()) }
    viewModel { SupervisedByViewModel(get(), get(), get()) }

    single { WorkManager.getInstance(get()) }
}
