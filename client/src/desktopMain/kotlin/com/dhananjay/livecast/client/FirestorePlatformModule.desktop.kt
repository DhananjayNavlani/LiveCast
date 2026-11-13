package com.dhananjay.livecast.client

import com.dhananjay.livecast.auth.IdTokenProvider
import com.dhananjay.livecast.firestore.grpc.DocumentStore
import com.dhananjay.livecast.firestore.grpc.EnvPropertiesFirestoreConfigSource
import com.dhananjay.livecast.firestore.grpc.FirestoreConfigSource
import com.dhananjay.livecast.firestore.grpc.FirestoreGrpcClient
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.dsl.onClose
import java.io.File

const val MISSING_CONFIG_MSG =
    "Missing FIREBASE_PROJECT_ID environment variable or firebase_project_id in ~/.livecast/config.properties"

actual val firestorePlatformModule: Module = module {

    single<FirestoreConfigSource> {
        EnvPropertiesFirestoreConfigSource(File(System.getProperty("user.home"), ".livecast"))
    }
    single<DocumentStore> {
        val config = checkNotNull(get<FirestoreConfigSource>().load())
        val idTokenProvider = get<IdTokenProvider>()
        FirestoreGrpcClient(
            config = config,
            tokenProvider = { forceRefresh -> idTokenProvider.getIdToken(forceRefresh) }
        )
    } onClose { (it as? AutoCloseable)?.close() }
}
