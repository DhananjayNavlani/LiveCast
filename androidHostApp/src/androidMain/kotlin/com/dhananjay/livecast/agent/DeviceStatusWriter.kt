package com.dhananjay.livecast.agent

import com.dhananjay.livecast.protocol.FirestorePaths
import com.dhananjay.livecast.protocol.GroupWrites
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

interface DeviceStatusWriter {
    suspend fun writeStatus(deviceId: String, accessibilityOn: Boolean, notificationsOn: Boolean): Result<Unit>
    suspend fun writeCheckReply(deviceId: String, checkId: String, accessibilityOn: Boolean, notificationsOn: Boolean): Result<Unit>
}

class FirestoreDeviceStatusWriter(
    private val firestore: FirebaseFirestore
) : DeviceStatusWriter {

    override suspend fun writeStatus(
        deviceId: String,
        accessibilityOn: Boolean,
        notificationsOn: Boolean
    ): Result<Unit> = runCatching {
        val docRef = firestore.document(FirestorePaths.device(deviceId))
        val fields = GroupWrites.statusFields(accessibilityOn, notificationsOn, FieldValue.serverTimestamp())
        docRef.update(fields).await()
    }

    override suspend fun writeCheckReply(
        deviceId: String,
        checkId: String,
        accessibilityOn: Boolean,
        notificationsOn: Boolean
    ): Result<Unit> = runCatching {
        val docRef = firestore.document(FirestorePaths.device(deviceId))
        val fields = GroupWrites.checkReplyFields(checkId, accessibilityOn, notificationsOn, FieldValue.serverTimestamp())
        docRef.update(fields).await()
    }
}
