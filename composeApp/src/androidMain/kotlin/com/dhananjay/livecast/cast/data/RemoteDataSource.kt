package com.dhananjay.livecast.cast.data

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow

class RemoteDataSource(
    private val firestore: FirebaseFirestore,
    private val crashlytics: FirebaseCrashlytics
) {
    fun getConfigCollectionFlow() = callbackFlow<Result<DocumentSnapshot>> {
        val configCollectionRef = firestore.collection("config")
        val configDocumentRef = configCollectionRef.document("android")

        val listener = configDocumentRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(Result.failure(error))
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                trySend(Result.success(snapshot))
            } else {
                trySend(Result.failure(Exception("Document does not exist")))
            }
        }

        awaitClose {
            listener.remove()
            close()
        }
    }
}