package com.dhananjay.livecast.cast.data

import com.dhananjay.livecast.cast.data.model.LiveCastUser
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

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

    suspend fun getUser(uid: String): Result<DocumentSnapshot> {
        val docRef = firestore.collection("users").document(uid)
        return try {
            Result.success(docRef.get().await())
        }catch (e: Exception){
            crashlytics.recordException(e)
            Result.failure(e)
        }
    }

    suspend fun userExists(uid: String): Boolean {
        val docRef = firestore.collection("users").document(uid)
        return try {
            val snapshot = docRef.get().await()
            snapshot.exists()
        }catch (e: Exception){
            crashlytics.recordException(e)
            false
        }
    }

    suspend fun addUser(user:LiveCastUser){
        val docRef = firestore.collection("users").document(user.uid)
        try {
            docRef.set(user, SetOptions.merge()).await()
        }catch (e: Exception){
            crashlytics.recordException(e)
        }
    }

}