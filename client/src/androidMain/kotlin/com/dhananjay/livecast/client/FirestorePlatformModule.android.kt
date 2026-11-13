package com.dhananjay.livecast.client

import com.google.firebase.firestore.FirebaseFirestore
import org.koin.core.module.Module
import org.koin.dsl.module

actual val firestorePlatformModule: Module = module {
    single { FirebaseFirestore.getInstance() }
}
