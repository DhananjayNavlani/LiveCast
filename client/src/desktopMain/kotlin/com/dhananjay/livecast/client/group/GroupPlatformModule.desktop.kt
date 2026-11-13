package com.dhananjay.livecast.client.group

import com.dhananjay.livecast.client.firestorePlatformModule
import com.dhananjay.livecast.firestore.grpc.DocumentStore
import com.dhananjay.livecast.firestore.grpc.FirestoreConfigSource
import org.koin.core.module.Module
import org.koin.dsl.module

private const val MISSING_CONFIG_MSG =
    "Missing FIREBASE_PROJECT_ID environment variable or firebase_project_id in ~/.livecast/config.properties"

actual val groupPlatformModule: Module = module {
    includes(firestorePlatformModule)
    single<GroupRepository> {
        get<FirestoreConfigSource>().load()?.let {
            DocumentStoreGroupRepository(get<DocumentStore>(), get())
        } ?: UnsupportedGroupRepository(MISSING_CONFIG_MSG)
    }
    single<GroupMemberRepository> {
        get<FirestoreConfigSource>().load()?.let {
            DocumentStoreGroupMemberRepository(get<DocumentStore>())
        } ?: UnsupportedGroupMemberRepository(MISSING_CONFIG_MSG)
    }
    single<GroupDeviceRepository> {
        get<FirestoreConfigSource>().load()?.let {
            DocumentStoreGroupDeviceRepository(get<DocumentStore>())
        } ?: UnsupportedGroupDeviceRepository(MISSING_CONFIG_MSG)
    }
}
