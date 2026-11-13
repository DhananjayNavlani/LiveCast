package com.dhananjay.livecast.pairing

import com.dhananjay.livecast.protocol.FirestorePaths
import com.dhananjay.livecast.protocol.Supervisor
import com.dhananjay.livecast.protocol.normalizeTimestamps
import com.dhananjay.livecast.protocol.supervisorFrom
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

interface SupervisorDirectory {
    fun observeSupervisors(deviceId: String): Flow<List<Supervisor>>
}

class FirestoreSupervisorDirectory(
    private val firestore: FirebaseFirestore
) : SupervisorDirectory {

    override fun observeSupervisors(deviceId: String): Flow<List<Supervisor>> = callbackFlow {
        val collectionRef = firestore.collection(FirestorePaths.DEVICES)
            .document(deviceId)
            .collection(FirestorePaths.SUPERVISORS)

        val registration = collectionRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val supervisors = snapshot.documents.mapNotNull { doc ->
                    val rawData = doc.data ?: return@mapNotNull null
                    val data = normalizeTimestamps(rawData, firestoreTimestampToMillis)
                    supervisorFrom(doc.id, data)
                }
                trySend(supervisors)
            }
        }
        awaitClose { registration.remove() }
    }
}
