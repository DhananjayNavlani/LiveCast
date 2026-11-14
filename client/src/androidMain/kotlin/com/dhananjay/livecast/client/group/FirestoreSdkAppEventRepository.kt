package com.dhananjay.livecast.client.group

import com.dhananjay.livecast.protocol.AppEvent
import com.dhananjay.livecast.protocol.FirestorePaths
import com.dhananjay.livecast.protocol.GroupFields
import com.dhananjay.livecast.protocol.appEventFrom
import com.dhananjay.livecast.protocol.normalizeTimestamps
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

private val toMillis: (Any) -> Long? = { (it as? Timestamp)?.toDate()?.time }

private fun Throwable.toGroupError(): Throwable = when {
    this is FirebaseFirestoreException && code == FirebaseFirestoreException.Code.PERMISSION_DENIED -> GroupError.AccessLost
    this is GroupError -> this
    cause is GroupError -> cause!!
    else -> this
}

class FirestoreSdkAppEventRepository(
    private val firestore: FirebaseFirestore,
) : AppEventRepository {

    override fun observeRecent(deviceId: String, groupId: String, limit: Int): Flow<List<AppEvent>> = callbackFlow {
        val query = firestore.collection(FirestorePaths.appEvents(deviceId))
            .whereEqualTo(GroupFields.GROUP_ID, groupId)
            .orderBy(GroupFields.AT, Query.Direction.DESCENDING)
            .limit(limit.toLong())

        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error.toGroupError())
                return@addSnapshotListener
            }

            if (snapshot != null) {
                val events = snapshot.documents.mapNotNull { doc ->
                    val rawData = doc.data ?: emptyMap<String, Any?>()
                    val data = normalizeTimestamps(rawData, toMillis)
                    appEventFrom(doc.id, data)
                }
                trySend(events)
            }
        }

        awaitClose { registration.remove() }
    }
}
