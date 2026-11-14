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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.tasks.await

private val toMillis: (Any) -> Long? = { (it as? Timestamp)?.toDate()?.time }

internal fun Throwable.toGroupError(): Throwable = when {
    this is FirebaseFirestoreException && code == FirebaseFirestoreException.Code.PERMISSION_DENIED -> GroupError.AccessLost
    this is GroupError -> this
    cause is GroupError -> cause!!
    else -> this
}

class FirestoreSdkGroupRepository(
    private val firestore: FirebaseFirestore,
    private val session: SessionProvider,
) : GroupRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeMyMemberships(): Flow<List<GroupMembership>> {
        return session.verifiedEmail().flatMapLatest { email ->
            if (email == null) {
                flowOf(emptyList())
            } else {
                callbackFlow {
                    val query = firestore.collectionGroup(FirestorePaths.MEMBERS)
                        .whereEqualTo(GroupFields.EMAIL, email)

                    val registration = query.addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            close(error.toGroupError())
                            return@addSnapshotListener
                        }

                        if (snapshot != null) {
                            val docs = snapshot.documents.map { doc ->
                                MemberDoc(
                                    path = doc.reference.path,
                                    id = doc.id,
                                    data = normalizeTimestamps(doc.data ?: emptyMap(), toMillis),
                                )
                            }
                            trySend(docs)
                        }
                    }

                    awaitClose { registration.remove() }
                }.mapLatest { docs ->
                    assembleMemberships(docs) { groupId ->
                        try {
                            val groupDoc = firestore.document(FirestorePaths.group(groupId)).get().await()
                            if (!groupDoc.exists()) return@assembleMemberships null
                            groupFrom(groupId, normalizeTimestamps(groupDoc.data ?: emptyMap(), toMillis))
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            null
                        }
                    }
                }
            }
        }
    }

    override suspend fun createGroup(name: String): Result<String> {
        val input = validateCreateGroup(session.currentUser, name).getOrElse {
            return Result.failure(it)
        }

        return runCatching {
            val groupRef = firestore.collection(FirestorePaths.GROUPS).document()
            val groupId = groupRef.id
            val memberRef = firestore.document(FirestorePaths.member(groupId, input.ownerEmail))
            val now = FieldValue.serverTimestamp()
            val groupFields = GroupWrites.groupFields(input.groupName, input.ownerUid, now)
            val memberFields = GroupWrites.memberFields(input.ownerEmail, GroupRole.OWNER, input.ownerName, now)

            val batch = firestore.batch()
            batch.set(groupRef, groupFields)
            batch.set(memberRef, memberFields)
            batch.commit().await()
            groupId
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { e ->
                if (e is CancellationException) throw e
                Result.failure(e.toGroupError())
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
                close(error.toGroupError())
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
                if (e is CancellationException) throw e
                val cause = (e as? FirebaseFirestoreException)?.cause ?: e
                if (cause is GroupError.AlreadyMember) {
                    Result.failure(cause)
                } else if (e is GroupError.AlreadyMember) {
                    Result.failure(e)
                } else {
                    Result.failure(e.toGroupError())
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
                if (e is CancellationException) throw e
                Result.failure(e.toGroupError())
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
                if (e is CancellationException) throw e
                Result.failure(e.toGroupError())
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
                close(error.toGroupError())
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
        return combineGroupDevices(flows)
    }

    override suspend fun removeFromGroup(deviceId: String): Result<Unit> {
        val docRef = firestore.document(FirestorePaths.device(deviceId))

        return runCatching {
            docRef.update(GroupWrites.removeFromGroupFields()).await()
            Unit
        }.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { e ->
                if (e is CancellationException) throw e
                Result.failure(e.toGroupError())
            },
        )
    }
}
