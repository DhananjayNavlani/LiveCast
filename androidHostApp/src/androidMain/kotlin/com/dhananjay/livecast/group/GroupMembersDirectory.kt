package com.dhananjay.livecast.group

import com.dhananjay.livecast.protocol.FirestorePaths
import com.dhananjay.livecast.protocol.GroupMember
import com.dhananjay.livecast.protocol.GroupRules
import com.dhananjay.livecast.protocol.groupMemberFrom
import com.dhananjay.livecast.protocol.normalizeTimestamps
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

interface GroupMembersDirectory {
    fun observeSupervisors(groupId: String): Flow<List<GroupMember>>
}

class FirestoreGroupMembersDirectory(
    private val firestore: FirebaseFirestore
) : GroupMembersDirectory {

    override fun observeSupervisors(groupId: String): Flow<List<GroupMember>> = callbackFlow {
        val collectionRef = firestore.collection(FirestorePaths.members(groupId))
        val registration = collectionRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val members = snapshot.documents.mapNotNull { doc ->
                    val rawData = doc.data ?: return@mapNotNull null
                    val data = normalizeTimestamps(rawData, firestoreTimestampToMillis)
                    groupMemberFrom(groupId, doc.id, data)
                }
                val supervisors = GroupRules.supervisorsOf(members)
                trySend(supervisors)
            }
        }
        awaitClose { registration.remove() }
    }
}
