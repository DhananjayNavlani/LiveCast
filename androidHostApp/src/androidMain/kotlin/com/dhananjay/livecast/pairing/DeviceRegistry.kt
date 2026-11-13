package com.dhananjay.livecast.pairing

import com.dhananjay.livecast.protocol.FirestorePaths
import com.dhananjay.livecast.protocol.HostDevice
import com.dhananjay.livecast.protocol.PairingFields
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

interface DeviceRegistry {
    suspend fun upsertDevice(device: HostDevice): Result<Unit>
}

class FirestoreDeviceRegistry(
    private val firestore: FirebaseFirestore
) : DeviceRegistry {

    override suspend fun upsertDevice(device: HostDevice): Result<Unit> = runCatching {
        val docRef = firestore.document(FirestorePaths.device(device.id))
        val data = hashMapOf<String, Any?>(
            PairingFields.HOST_UID to device.id,
            PairingFields.DISPLAY_NAME to device.displayName,
            PairingFields.MODEL to device.model,
            PairingFields.ONLINE to device.online,
            PairingFields.LAST_SEEN_EPOCH_MS to device.lastSeenEpochMs,
            PairingFields.CREATED_AT to Timestamp.now()
        )
        docRef.set(data, SetOptions.merge()).await()
    }
}
