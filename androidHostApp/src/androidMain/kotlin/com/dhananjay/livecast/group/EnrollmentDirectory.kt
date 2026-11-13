package com.dhananjay.livecast.group

import com.dhananjay.livecast.protocol.FirestorePaths
import com.dhananjay.livecast.protocol.Group
import com.dhananjay.livecast.protocol.GroupFields
import com.dhananjay.livecast.protocol.GroupMember
import com.dhananjay.livecast.protocol.GroupRole
import com.dhananjay.livecast.protocol.GroupRules
import com.dhananjay.livecast.protocol.groupFrom
import com.dhananjay.livecast.protocol.groupMemberFrom
import com.dhananjay.livecast.protocol.normalizeTimestamps
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

data class EnrollmentCandidate(
    val group: Group,
    val supervisors: List<GroupMember>
)

interface EnrollmentDirectory {
    suspend fun candidates(email: String): Result<List<EnrollmentCandidate>>
}

class FirestoreEnrollmentDirectory(
    private val firestore: FirebaseFirestore
) : EnrollmentDirectory {

    override suspend fun candidates(email: String): Result<List<EnrollmentCandidate>> = runCatching {
        val normalizedEmail = GroupRules.normalizeEmail(email)
        val memberDocs = firestore.collectionGroup(FirestorePaths.MEMBERS)
            .whereEqualTo(GroupFields.EMAIL, normalizedEmail)
            .get()
            .await()

        val groupIds = memberDocs.documents.mapNotNull { doc ->
            val rawData = doc.data ?: return@mapNotNull null
            val data = normalizeTimestamps(rawData, firestoreTimestampToMillis)
            val parentGroupId = doc.reference.parent.parent?.id
                ?: FirestorePaths.groupIdFromMemberPath(doc.reference.path)
                ?: return@mapNotNull null
            val member = groupMemberFrom(parentGroupId, doc.id, data)
            if (member?.role == GroupRole.DEVICE) parentGroupId else null
        }.distinct()

        groupIds.mapNotNull { groupId ->
            val groupDoc = firestore.document(FirestorePaths.group(groupId)).get().await()
            if (!groupDoc.exists()) return@mapNotNull null
            val rawGroupData = groupDoc.data ?: return@mapNotNull null
            val groupData = normalizeTimestamps(rawGroupData, firestoreTimestampToMillis)
            val group = groupFrom(groupDoc.id, groupData) ?: return@mapNotNull null

            val membersSnapshot = firestore.collection(FirestorePaths.members(groupId)).get().await()
            val allMembers = membersSnapshot.documents.mapNotNull { doc ->
                val rawData = doc.data ?: return@mapNotNull null
                val data = normalizeTimestamps(rawData, firestoreTimestampToMillis)
                groupMemberFrom(groupId, doc.id, data)
            }
            val supervisors = GroupRules.supervisorsOf(allMembers)
            EnrollmentCandidate(group = group, supervisors = supervisors)
        }
    }
}
