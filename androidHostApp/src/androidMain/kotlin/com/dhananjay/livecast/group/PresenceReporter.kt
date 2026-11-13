package com.dhananjay.livecast.group

import com.dhananjay.livecast.protocol.FirestorePaths
import com.dhananjay.livecast.protocol.GroupWrites
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

interface PresenceReporter {
    suspend fun report(deviceId: String, online: Boolean): Result<Unit>
}

class FirestorePresenceReporter(
    private val firestore: FirebaseFirestore
) : PresenceReporter {

    override suspend fun report(deviceId: String, online: Boolean): Result<Unit> = runCatching {
        val docRef = firestore.document(FirestorePaths.device(deviceId))
        val fields = GroupWrites.heartbeatFields(online, FieldValue.serverTimestamp())
        docRef.update(fields).await()
    }
}
