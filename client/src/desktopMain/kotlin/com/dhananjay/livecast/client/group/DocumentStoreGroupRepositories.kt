package com.dhananjay.livecast.client.group

import com.dhananjay.livecast.auth.SessionProvider
import com.dhananjay.livecast.firestore.grpc.DocumentStore
import com.dhananjay.livecast.firestore.grpc.FirestoreAlreadyExistsException
import com.dhananjay.livecast.firestore.grpc.FirestorePermissionDeniedException
import com.dhananjay.livecast.firestore.grpc.FirestoreQuery
import com.dhananjay.livecast.firestore.grpc.FirestoreWrite
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Instant

private val toMillis: (Any) -> Long? = { (it as? Instant)?.toEpochMilli() }

class DocumentStoreGroupRepository(
    private val store: DocumentStore,
    private val session: SessionProvider,
) : GroupRepository {

    override fun observeMyMemberships(): Flow<List<GroupMembership>> {
        val user = session.currentUser
        val email = user?.email
        if (user == null || email.isNullOrBlank() || user.isAnonymous) {
            return flowOf(emptyList())
        }

        val normalizedEmail = GroupRules.normalizeEmail(email)
        val query = FirestoreQuery(
            parentPath = "",
            collectionId = FirestorePaths.MEMBERS,
            allDescendants = true,
            whereEquals = listOf(GroupFields.EMAIL to normalizedEmail),
        )

        return store.listenQuery(query)
            .map { memberDocs ->
                memberDocs.mapNotNull { memberDoc ->
                    val groupId = FirestorePaths.groupIdFromMemberPath(memberDoc.path) ?: return@mapNotNull null
                    val memberData = normalizeTimestamps(memberDoc.fields, toMillis)
                    val member = groupMemberFrom(groupId, memberDoc.id, memberData) ?: return@mapNotNull null
                    val groupDoc = try {
                        store.get(FirestorePaths.group(groupId))
                    } catch (_: Exception) {
                        null
                    } ?: return@mapNotNull null
                    val groupData = normalizeTimestamps(groupDoc.fields, toMillis)
                    val group = groupFrom(groupId, groupData) ?: return@mapNotNull null
                    GroupMembership(group, member.role)
                }
            }
            .catch { e ->
                if (e is FirestorePermissionDeniedException) {
                    throw GroupError.AccessLost
                } else {
                    throw e
                }
            }
    }

    override suspend fun createGroup(name: String): Result<String> {
        val user = session.currentUser ?: return Result.failure(GroupError.NotSignedIn)
        val email = user.email
        if (user.isAnonymous || email.isNullOrBlank()) return Result.failure(GroupError.NotSignedIn)
        if (!user.emailVerified) return Result.failure(GroupError.EmailNotVerified)
        val normalizedName = GroupRules.normalizeGroupName(name)
            ?: return Result.failure(IllegalArgumentException("Enter a group name (up to 60 characters)"))

        val groupId = GroupWrites.newGroupId()
        val now = Instant.now()
        val groupPath = FirestorePaths.group(groupId)
        val memberPath = FirestorePaths.member(groupId, email)
        val groupFields = GroupWrites.groupFields(normalizedName, user.id, now)
        val memberFields = GroupWrites.memberFields(email, GroupRole.OWNER, user.displayName, now)

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
                if (e is FirestorePermissionDeniedException) {
                    Result.failure(GroupError.AccessLost)
                } else {
                    Result.failure(e)
                }
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
                if (e is FirestorePermissionDeniedException) {
                    throw GroupError.AccessLost
                } else {
                    throw e
                }
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
                when (e) {
                    is FirestoreAlreadyExistsException -> Result.failure(GroupError.AlreadyMember(normalizedEmail))
                    is FirestorePermissionDeniedException -> Result.failure(GroupError.AccessLost)
                    else -> Result.failure(e)
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
                if (e is FirestorePermissionDeniedException) {
                    Result.failure(GroupError.AccessLost)
                } else {
                    Result.failure(e)
                }
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
                if (e is FirestorePermissionDeniedException) {
                    Result.failure(GroupError.AccessLost)
                } else {
                    Result.failure(e)
                }
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
            }
        }

        return combine(flows) { deviceLists ->
            deviceLists.flatMap { it }
                .distinctBy { it.id }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayName })
        }.catch { e ->
            if (e is FirestorePermissionDeniedException) {
                throw GroupError.AccessLost
            } else {
                throw e
            }
        }
    }

    override suspend fun removeFromGroup(deviceId: String): Result<Unit> {
        val path = FirestorePaths.device(deviceId)
        val fields = GroupWrites.removeFromGroupFields()

        return runCatching {
            store.commit(listOf(FirestoreWrite.Update(path, fields)))
        }.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { e ->
                if (e is FirestorePermissionDeniedException) {
                    Result.failure(GroupError.AccessLost)
                } else {
                    Result.failure(e)
                }
            },
        )
    }
}
