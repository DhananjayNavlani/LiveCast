package com.dhananjay.livecast.cast.data

import android.util.Log
import com.dhananjay.livecast.cast.data.model.DeviceOnline
import com.dhananjay.livecast.cast.data.model.LiveCastUser
import com.dhananjay.livecast.cast.utils.Constants
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.toObject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.coroutineContext

class RemoteDataSource(
    private val firestore: FirebaseFirestore,
    private val crashlytics: FirebaseCrashlytics
) {
    private val TAG = javaClass.simpleName
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

    val devicesOnline = callbackFlow {
        val listener = firestore.collection("rooms").document("online")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    Log.e(TAG, "Error fetching offer: $error")
                    return@addSnapshotListener
                }

                snapshot.toObject(DeviceOnline::class.java)?.let {
                    trySend(it)
                }
            }
        awaitClose { listener.remove() }
    }


    suspend fun addDeviceOnline(): Boolean {
        return try {
            firestore.collection("rooms").document("online").let {
                val deviceOnline = it.get().await().takeIf { it.exists() }?.toObject<DeviceOnline>()
                val hasDevice = deviceOnline?.devices?.contains(Constants.DEVICE_ID) ?: false

                Log.d(TAG, "addDeviceOnline: The collection has device ? $deviceOnline")
                if (!hasDevice) {
                    var count = deviceOnline?.count?.coerceAtLeast(0) ?: 0
                    it.set(
                        hashMapOf(
                            "count" to ++count,
                            "names" to FieldValue.arrayUnion(Constants.DEVICE_NAME),
                            "devices" to FieldValue.arrayUnion(Constants.DEVICE_ID)
                        ),
                        SetOptions.merge()
                    ).await()
                }

            }

            true
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            Log.e(TAG, "Error adding device online: $e")
            false
        }

    }

    suspend fun removeDeviceOnline(): Boolean {
        return try {
            firestore.collection("rooms").document("online").let {
                val deviceOnline = it.get().await().takeIf { it.exists() }?.toObject<DeviceOnline>()
                val hasDevice = deviceOnline?.devices?.contains(Constants.DEVICE_ID) ?: false

                Log.d(TAG, "removeDeviceOnline: The collection has device ? $deviceOnline")
                if (hasDevice) {
                    var count = deviceOnline?.count?.coerceAtLeast(1) ?: 1
                    it.set(
                        hashMapOf(
                            "count" to --count,
                            "names" to FieldValue.arrayRemove(Constants.DEVICE_NAME),
                            "devices" to FieldValue.arrayRemove(Constants.DEVICE_ID)
                        ),
                        SetOptions.merge()
                    ).await()
                }
            }
            true
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            Log.e(TAG, "Error removing device online: $e")
            false
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