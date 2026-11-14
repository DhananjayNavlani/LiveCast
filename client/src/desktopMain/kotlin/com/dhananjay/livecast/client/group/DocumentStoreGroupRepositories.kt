package com.dhananjay.livecast.client.group

import com.dhananjay.livecast.auth.SessionProvider
import com.dhananjay.livecast.firestore.grpc.DocumentStore
import com.dhananjay.livecast.firestore.grpc.FirestoreAlreadyExistsException
import com.dhananjay.livecast.firestore.grpc.FirestorePermissionDeniedException
import com.dhananjay.livecast.firestore.grpc.FirestoreQuery
import com.dhananjay.livecast.firestore.grpc.FirestoreWrite
import com.dhananjay.livecast.protocol.FirestorePaths
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import java.time.Instant

private val toMillis: (Any) -> Long? = { (it as? Instant)?.toEpochMilli() }

internal fun Throwable.toGroupError(): Throwable = when (this) {
    is FirestorePermissionDeniedException -> GroupError.AccessLost
    else -> this
}

class DocumentStoreGroupRepository(
    private val store: DocumentStore,
    private val session: SessionProvider,
) : GroupRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeMyMemberships(): Flow<List<GroupMembership>> {
        return session.verifiedEmail().flatMapLatest { email ->
            if (email == null) {
                flowOf(emptyList())
            } else {
                val query = FirestoreQuery(
                    parentPath = "",
                    collectionId = FirestorePaths.MEMBERS,
                    allDescendants = true,
                    whereEquals = listOf(GroupFields.EMAIL to email),
                )
                store.listenQuery(query).mapLatest { memberDocs ->
                    val docs = memberDocs.map { doc ->
                        MemberDoc(
                            path = doc.path,
                            id = doc.id,
                            data = normalizeTimestamps(doc.fields, toMillis),
                        )
                    }
                    assembleMemberships(docs) { groupId ->
                        try {
                            val groupDoc = store.get(FirestorePaths.group(groupId)) ?: return@assembleMemberships null
                            groupFrom(groupId, normalizeTimestamps(groupDoc.fields, toMillis))
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            null
                        }
                    }
                }.catch { e ->
                    throw e.toGroupError()
                }
            }
        }
    }

    override suspend fun createGroup(name: String): Result<String> {
        val input = validateCreateGroup(session.currentUser, name).getOrElse {
            return Result.failure(it)
        }

        val groupId = GroupWrites.newGroupId()
        val now = Instant.now()
        val groupPath = FirestorePaths.group(groupId)
        val memberPath = FirestorePaths.member(groupId, input.ownerEmail)
        val groupFields = GroupWrites.groupFields(input.groupName, input.ownerUid, now)
        val memberFields = GroupWrites.memberFields(input.ownerEmail, GroupRole.OWNER, input.ownerName, now)

        val writes = listOf(
            FirestoreWrite.Set(groupPath, groupFields, mustNotExist = true),
            FirestoreWrite.Set(memberPath, memberFields, mustNotExist = true),
        )

        return runCatching {
            store.commit(writes)
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

class DocumentStoreGroupMemberRepository(
    private val store: DocumentStore,
) : GroupMemberRepository {

    override fun observeMembers(groupId: String): Flow<List<GroupMember>> {
        val query = FirestoreQuery(
            parentPath = FirestorePaths.group(groupId),
            collectionId = FirestorePaths.MEMBERS,
            allDescendants = false,
        )
        return store.listenQuery(query)
            .map { docs ->
                docs.mapNotNull { doc ->
                    val data = normalizeTimestamps(doc.fields, toMillis)
                    groupMemberFrom(groupId, doc.id, data)
                }
            }
            .catch { e ->
                throw e.toGroupError()
            }
    }

    override suspend fun addMember(
        groupId: String,
        email: String,
        role: GroupRole,
        name: String?,
    ): Result<Unit> {
        val normalizedEmail = GroupRules.normalizeEmail(email)
        val path = FirestorePaths.member(groupId, normalizedEmail)
        val now = Instant.now()
        val fields = GroupWrites.memberFields(normalizedEmail, role, name, now)

        return runCatching {
            store.commit(listOf(FirestoreWrite.Set(path, fields, mustNotExist = true)))
        }.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { e ->
                if (e is CancellationException) throw e
                when (e) {
                    is FirestoreAlreadyExistsException -> Result.failure(GroupError.AlreadyMember(normalizedEmail))
                    else -> Result.failure(e.toGroupError())
                }
            },
        )
    }

    override suspend fun changeRole(groupId: String, email: String, role: GroupRole): Result<Unit> {
        val path = FirestorePaths.member(groupId, email)
        val fields = mapOf<String, Any?>(GroupFields.ROLE to GroupRules.roleWire(role))

        return runCatching {
            store.commit(listOf(FirestoreWrite.Update(path, fields)))
        }.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { e ->
                if (e is CancellationException) throw e
                Result.failure(e.toGroupError())
            },
        )
    }

    override suspend fun removeMember(groupId: String, email: String): Result<Unit> {
        val path = FirestorePaths.member(groupId, email)

        return runCatching {
            store.commit(listOf(FirestoreWrite.Delete(path)))
        }.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { e ->
                if (e is CancellationException) throw e
                Result.failure(e.toGroupError())
            },
        )
    }
}

class DocumentStoreGroupDeviceRepository(
    private val store: DocumentStore,
) : GroupDeviceRepository {

    override fun observeDevices(groupIds: List<String>): Flow<List<HostDevice>> {
        val distinctIds = groupIds.distinct()
        if (distinctIds.isEmpty()) return flowOf(emptyList())

        val flows = distinctIds.map { groupId ->
            val query = FirestoreQuery(
                parentPath = "",
                collectionId = FirestorePaths.DEVICES,
                allDescendants = false,
                whereEquals = listOf(GroupFields.GROUP_ID to groupId),
            )
            store.listenQuery(query).map { docs ->
                docs.mapNotNull { doc ->
                    val data = normalizeTimestamps(doc.fields, toMillis)
                    hostDeviceFrom(doc.id, data)
                }
            }.catch { e ->
                throw e.toGroupError()
            }
        }

        return combineGroupDevices(flows)
    }

    override suspend fun removeFromGroup(deviceId: String): Result<Unit> {
        val path = FirestorePaths.device(deviceId)
        val fields = GroupWrites.removeFromGroupFields()

        return runCatching {
            store.commit(listOf(FirestoreWrite.Update(path, fields)))
        }.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { e ->
                if (e is CancellationException) throw e
                Result.failure(e.toGroupError())
            },
        )
    }
}
