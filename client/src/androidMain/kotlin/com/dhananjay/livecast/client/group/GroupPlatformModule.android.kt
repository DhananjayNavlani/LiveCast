package com.dhananjay.livecast.client.group

import com.dhananjay.livecast.client.firestorePlatformModule
import org.koin.core.module.Module
import org.koin.dsl.module

actual val groupPlatformModule: Module = module {
    includes(firestorePlatformModule)
    single<GroupRepository> { FirestoreSdkGroupRepository(get(), get()) }
    single<GroupMemberRepository> { FirestoreSdkGroupMemberRepository(get()) }
    single<GroupDeviceRepository> { FirestoreSdkGroupDeviceRepository(get()) }
}
