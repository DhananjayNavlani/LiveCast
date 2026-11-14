package com.dhananjay.livecast.di

import android.util.Log
import androidx.work.WorkManager
import com.dhananjay.livecast.MainViewModel
import com.dhananjay.livecast.agent.AgentNotifications
import com.dhananjay.livecast.agent.AgentOffNoticeChecker
import com.dhananjay.livecast.agent.AccessibilityStateReporter
import com.dhananjay.livecast.agent.AndroidAgentNotifications
import com.dhananjay.livecast.agent.AndroidDeviceStatusSource
import com.dhananjay.livecast.agent.AndroidInstalledAppsSource
import com.dhananjay.livecast.agent.AndroidPackageChangeRegistrar
import com.dhananjay.livecast.agent.AppBaselineStore
import com.dhananjay.livecast.agent.AppChangeMonitor
import com.dhananjay.livecast.agent.AppEventWriter
import com.dhananjay.livecast.agent.AppScopeEnrollmentNotifier
import com.dhananjay.livecast.agent.CheckResponder
import com.dhananjay.livecast.agent.DeviceRegistryEnrollmentState
import com.dhananjay.livecast.agent.DeviceStatusSource
import com.dhananjay.livecast.agent.DeviceStatusWriter
import com.dhananjay.livecast.agent.EnrollmentListener
import com.dhananjay.livecast.agent.EnrollmentNotifier
import com.dhananjay.livecast.agent.EnrollmentState
import com.dhananjay.livecast.agent.FirebasePushTokenSource
import com.dhananjay.livecast.agent.FirestoreAppEventWriter
import com.dhananjay.livecast.agent.FirestoreDeviceStatusWriter
import com.dhananjay.livecast.agent.FirestorePushTokenStore
import com.dhananjay.livecast.agent.HostAgent
import com.dhananjay.livecast.agent.HostAgentControl
import com.dhananjay.livecast.agent.HostAgentRestartListener
import com.dhananjay.livecast.agent.InstalledAppsSource
import com.dhananjay.livecast.agent.PackageChangeRegistrar
import com.dhananjay.livecast.agent.PushTokenRegistry
import com.dhananjay.livecast.agent.PushTokenSource
import com.dhananjay.livecast.agent.PushTokenStore
import com.dhananjay.livecast.agent.SharedPreferencesAppBaselineStore
import com.dhananjay.livecast.agent.SupervisionNotifier
import com.dhananjay.livecast.agent.SystemWallClock
import com.dhananjay.livecast.agent.WallClock
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
import com.dhananjay.livecast.group.GroupMembersDirectory
import com.dhananjay.livecast.group.HostDeviceInfoProvider
import com.dhananjay.livecast.group.SupervisedByViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.qualifier.named
import com.dhananjay.livecast.webrtc.connection.SignalingClient
import com.dhananjay.livecast.webrtc.peer.StreamPeerConnectionFactory
import com.dhananjay.livecast.webrtc.session.WebRtcSessionManager
import com.dhananjay.livecast.webrtc.session.WebRtcSessionManagerImpl
import com.firebase.ui.auth.AuthUI
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.dsl.worker
import org.koin.core.module.dsl.bind
import org.koin.dsl.bind
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
    single<EnrollmentDirectory> { FirestoreEnrollmentDirectory(get()) }
    single<GroupMembersDirectory> { FirestoreGroupMembersDirectory(get()) }
    single<EnrollmentNotifier> {
        AppScopeEnrollmentNotifier(
            listOf(get<PushTokenRegistry>(), get<HostAgentRestartListener>()),
            get(named("appScope"))
        )
    }
    viewModel { EnrollmentViewModel(get(), get(), get(), get(), get(), get()) }
    viewModel { SupervisedByViewModel(get(), get(), get()) }

    single { FirebaseMessaging.getInstance() }
    single(named("appScope")) {
        val handler = CoroutineExceptionHandler { _, throwable ->
            Log.w("AppScope", "Unhandled exception in appScope", throwable)
        }
        CoroutineScope(SupervisorJob() + Dispatchers.IO + handler)
    }
    single<DeviceStatusSource> { AndroidDeviceStatusSource(androidContext()) }
    single<DeviceStatusWriter> { FirestoreDeviceStatusWriter(get()) }
    single<EnrollmentState> { DeviceRegistryEnrollmentState(get(), get()) }
    single<PushTokenStore> { FirestorePushTokenStore(get()) }
    single<PushTokenSource> { FirebasePushTokenSource(get()) }
    single { PushTokenRegistry(get(), get(), get(), get()) }
    single<AgentNotifications> { AndroidAgentNotifications(androidContext()) }
    single { CheckResponder(get(), get(), get(), get()) }

    single<WallClock> { SystemWallClock() }
    single<InstalledAppsSource> { AndroidInstalledAppsSource(androidContext()) }
    single<AppBaselineStore> { SharedPreferencesAppBaselineStore(androidContext()) }
    single<AppEventWriter> { FirestoreAppEventWriter(get()) }
    single<PackageChangeRegistrar> { AndroidPackageChangeRegistrar(androidContext(), get()) }
    single { AccessibilityStateReporter(get(), get(), get(), get(), get(named("appScope"))) }
    single { AppChangeMonitor(get(), get(), get(), get(), get(), get(), get()) }
    single { SupervisionNotifier(get(), get(), get(), get(named("appScope"))) }
    single {
        HostAgent(
            listOf(get<AccessibilityStateReporter>(), get<AppChangeMonitor>(), get<SupervisionNotifier>()),
            get(named("appScope"))
        )
    } bind HostAgentControl::class
    single { HostAgentRestartListener(get<HostAgentControl>()) }
    single { AgentOffNoticeChecker(get(), get(), get()) }

    single { WorkManager.getInstance(get()) }
}
