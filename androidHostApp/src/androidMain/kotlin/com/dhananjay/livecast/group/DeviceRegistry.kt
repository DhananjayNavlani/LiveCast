package com.dhananjay.livecast.group

import com.dhananjay.livecast.protocol.FirestorePaths
import com.dhananjay.livecast.protocol.GroupWrites
import com.dhananjay.livecast.protocol.HostDevice
import com.dhananjay.livecast.protocol.hostDeviceFrom
import com.dhananjay.livecast.protocol.normalizeTimestamps
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

interface DeviceRegistry {
    fun observeDevice(deviceId: String): Flow<HostDevice?>
    suspend fun enroll(device: HostDevice, groupId: String): Result<Unit>
}

class FirestoreDeviceRegistry(
    private val firestore: FirebaseFirestore
) : DeviceRegistry {

    override fun observeDevice(deviceId: String): Flow<HostDevice?> = callbackFlow {
        val docRef = firestore.document(FirestorePaths.device(deviceId))
        val registration = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                if (!snapshot.exists()) {
                    trySend(null)
                } else {
                    val rawData = snapshot.data ?: emptyMap<String, Any?>()
                    val data = normalizeTimestamps(rawData, firestoreTimestampToMillis)
                    trySend(hostDeviceFrom(snapshot.id, data))
                }
            }
        }
        awaitClose { registration.remove() }
    }

    override suspend fun enroll(device: HostDevice, groupId: String): Result<Unit> = runCatching {
        val docRef = firestore.document(FirestorePaths.device(device.id))
        val fields = GroupWrites.enrollFields(device, groupId, FieldValue.serverTimestamp())
        docRef.set(fields, SetOptions.merge()).await()
    }
}
