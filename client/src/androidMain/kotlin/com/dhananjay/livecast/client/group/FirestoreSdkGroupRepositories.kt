package com.dhananjay.livecast.client.group

import com.dhananjay.livecast.auth.SessionProvider
import com.dhananjay.livecast.protocol.FirestorePaths
import com.dhananjay.livecast.protocol.Group
import com.dhananjay.livecast.protocol.GroupFields
import com.dhananjay.livecast.protocol.GroupMember
import com.dhananjay.livecast.protocol.GroupMembership
import com.dhananjay.livecast.protocol.GroupRole
import com.dhananjay.livecast.protocol.GroupRules
import com.dhananjay.livecast.protocol.GroupWrites
import com.dhananjay.livecast.protocol.HostDevice

import com.dhananjay.livecast.protocol.groupFrom
import com.dhananjay.livecast.protocol.groupMemberFrom
import com.dhananjay.livecast.protocol.hostDeviceFrom
import com.dhananjay.livecast.protocol.normalizeTimestamps
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private val toMillis: (Any) -> Long? = { (it as? Timestamp)?.toDate()?.time }

class FirestoreSdkGroupRepository(
    private val firestore: FirebaseFirestore,
    private val session: SessionProvider,
) : GroupRepository {

    override fun observeMyMemberships(): Flow<List<GroupMembership>> = callbackFlow {
        val user = session.currentUser
        val email = user?.email
        if (user == null || email.isNullOrBlank() || user.isAnonymous) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val normalizedEmail = GroupRules.normalizeEmail(email)
        val query = firestore.collectionGroup(FirestorePaths.MEMBERS)
            .whereEqualTo(GroupFields.EMAIL, normalizedEmail)

        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                if (error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                    close(GroupError.AccessLost)
                } else {
                    close(error)
                }
                return@addSnapshotListener
            }

            if (snapshot != null) {
                launch {
                    val memberships = snapshot.documents.mapNotNull { doc ->
                        val groupId = FirestorePaths.groupIdFromMemberPath(doc.reference.path) ?: return@mapNotNull null
                        val rawData = doc.data ?: emptyMap<String, Any?>()
                        val memberData = normalizeTimestamps(rawData, toMillis)
                        val member = groupMemberFrom(groupId, doc.id, memberData) ?: return@mapNotNull null
                        val groupDoc = try {
                            firestore.document(FirestorePaths.group(groupId)).get().await()
                        } catch (_: Exception) {
                            null
                        }
                        if (groupDoc == null || !groupDoc.exists()) return@mapNotNull null
                        val rawGroupData = groupDoc.data ?: emptyMap<String, Any?>()
                        val groupData = normalizeTimestamps(rawGroupData, toMillis)
                        val group = groupFrom(groupId, groupData) ?: return@mapNotNull null
                        GroupMembership(group, member.role)
                    }
                    trySend(memberships)
                }
            }
        }

        awaitClose { registration.remove() }
    }

    override suspend fun createGroup(name: String): Result<String> {
        val user = session.currentUser ?: return Result.failure(GroupError.NotSignedIn)
        val email = user.email
        if (user.isAnonymous || email.isNullOrBlank()) return Result.failure(GroupError.NotSignedIn)
        if (!user.emailVerified) return Result.failure(GroupError.EmailNotVerified)
        val normalizedName = GroupRules.normalizeGroupName(name)
            ?: return Result.failure(IllegalArgumentException("Enter a group name (up to 60 characters)"))

        return runCatching {
            val groupRef = firestore.collection(FirestorePaths.GROUPS).document()
            val groupId = groupRef.id
            val memberRef = firestore.document(FirestorePaths.member(groupId, email))
            val now = FieldValue.serverTimestamp()
            val groupFields = GroupWrites.groupFields(normalizedName, user.id, now)
            val memberFields = GroupWrites.memberFields(email, GroupRole.OWNER, user.displayName, now)

            val batch = firestore.batch()
            batch.set(groupRef, groupFields)
            batch.set(memberRef, memberFields)
            batch.commit().await()
            groupId
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { e ->
                if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                    Result.failure(GroupError.AccessLost)
                } else {
                    Result.failure(e)
                }
            },
        )
    }
}

class FirestoreSdkGroupMemberRepository(
    private val firestore: FirebaseFirestore,
) : GroupMemberRepository {

    override fun observeMembers(groupId: String): Flow<List<GroupMember>> = callbackFlow {
        val query = firestore.collection(FirestorePaths.members(groupId))
        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                if (error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                    close(GroupError.AccessLost)
                } else {
                    close(error)
                }
                return@addSnapshotListener
            }

            if (snapshot != null) {
                val members = snapshot.documents.mapNotNull { doc ->
                    val rawData = doc.data ?: emptyMap<String, Any?>()
                    val data = normalizeTimestamps(rawData, toMillis)
                    groupMemberFrom(groupId, doc.id, data)
                }
                trySend(members)
            }
        }

        awaitClose { registration.remove() }
    }

    override suspend fun addMember(
        groupId: String,
        email: String,
        role: GroupRole,
        name: String?,
    ): Result<Unit> {
        val normalizedEmail = GroupRules.normalizeEmail(email)
        val docRef = firestore.document(FirestorePaths.member(groupId, normalizedEmail))
        val now = FieldValue.serverTimestamp()
        val fields = GroupWrites.memberFields(normalizedEmail, role, name, now)

        return runCatching {
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                if (snapshot.exists()) {
                    throw GroupError.AlreadyMember(normalizedEmail)
                }
                transaction.set(docRef, fields)
                null
            }.await()
            Unit
        }.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { e ->
                val cause = (e as? FirebaseFirestoreException)?.cause ?: e
                if (cause is GroupError.AlreadyMember) {
                    Result.failure(cause)
                } else if (e is GroupError.AlreadyMember) {
                    Result.failure(e)
                } else if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                    Result.failure(GroupError.AccessLost)
                } else {
                    Result.failure(e)
                }
            },
        )
    }

    override suspend fun changeRole(groupId: String, email: String, role: GroupRole): Result<Unit> {
        val docRef = firestore.document(FirestorePaths.member(groupId, email))
        val fields = mapOf<String, Any?>(GroupFields.ROLE to GroupRules.roleWire(role))

        return runCatching {
            docRef.update(fields).await()
            Unit
        }.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { e ->
                if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                    Result.failure(GroupError.AccessLost)
                } else {
                    Result.failure(e)
                }
            },
        )
    }

    override suspend fun removeMember(groupId: String, email: String): Result<Unit> {
        val docRef = firestore.document(FirestorePaths.member(groupId, email))

        return runCatching {
            docRef.delete().await()
            Unit
        }.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { e ->
                if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                    Result.failure(GroupError.AccessLost)
                } else {
                    Result.failure(e)
                }
            },
        )
    }
}

class FirestoreSdkGroupDeviceRepository(
    private val firestore: FirebaseFirestore,
) : GroupDeviceRepository {

    private fun observeDevicesForGroup(groupId: String): Flow<List<HostDevice>> = callbackFlow {
        val query = firestore.collection(FirestorePaths.DEVICES)
            .whereEqualTo(GroupFields.GROUP_ID, groupId)

        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                if (error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                    close(GroupError.AccessLost)
                } else {
                    close(error)
                }
                return@addSnapshotListener
            }

            if (snapshot != null) {
                val devices = snapshot.documents.mapNotNull { doc ->
                    val rawData = doc.data ?: emptyMap<String, Any?>()
                    val data = normalizeTimestamps(rawData, toMillis)
                    hostDeviceFrom(doc.id, data)
                }
                trySend(devices)
            }
        }

        awaitClose { registration.remove() }
    }

    override fun observeDevices(groupIds: List<String>): Flow<List<HostDevice>> {
        val distinctIds = groupIds.distinct()
        if (distinctIds.isEmpty()) return flowOf(emptyList())

        val flows = distinctIds.map { observeDevicesForGroup(it) }
        return combine(flows) { deviceLists ->
            deviceLists.flatMap { it }
                .distinctBy { it.id }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayName })
        }
    }

    override suspend fun removeFromGroup(deviceId: String): Result<Unit> {
        val docRef = firestore.document(FirestorePaths.device(deviceId))

        return runCatching {
            docRef.update(GroupWrites.removeFromGroupFields()).await()
            Unit
        }.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { e ->
                if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                    Result.failure(GroupError.AccessLost)
                } else {
                    Result.failure(e)
                }
            },
        )
    }
}
