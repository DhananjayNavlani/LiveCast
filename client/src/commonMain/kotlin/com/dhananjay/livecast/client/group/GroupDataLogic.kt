package com.dhananjay.livecast.client.group

import com.dhananjay.livecast.auth.AuthState
import com.dhananjay.livecast.auth.SessionProvider
import com.dhananjay.livecast.auth.User
import com.dhananjay.livecast.protocol.FirestorePaths
import com.dhananjay.livecast.protocol.Group
import com.dhananjay.livecast.protocol.GroupMembership
import com.dhananjay.livecast.protocol.GroupRules
import com.dhananjay.livecast.protocol.HostDevice
import com.dhananjay.livecast.protocol.groupMemberFrom
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

fun SessionProvider.verifiedEmail(): Flow<String?> = authState.map { state ->
    when (state) {
        is AuthState.Authenticated -> {
            val user = state.user
            val email = user.email
            if (!user.isAnonymous && user.emailVerified && !email.isNullOrBlank()) {
                GroupRules.normalizeEmail(email)
            } else {
                null
            }
        }
        else -> null
    }
}.distinctUntilChanged()

data class CreateGroupInput(
    val ownerUid: String,
    val ownerEmail: String,
    val ownerName: String?,
    val groupName: String,
)

fun validateCreateGroup(user: User?, name: String): Result<CreateGroupInput> {
    val email = user?.email
    if (user == null || user.isAnonymous || email.isNullOrBlank()) {
        return Result.failure(GroupError.NotSignedIn)
    }
    if (!user.emailVerified) {
        return Result.failure(GroupError.EmailNotVerified)
    }
    val normalizedName = GroupRules.normalizeGroupName(name)
        ?: return Result.failure(IllegalArgumentException("Enter a group name (up to 60 characters)"))
    val normalizedEmail = GroupRules.normalizeEmail(email)
    return Result.success(
        CreateGroupInput(
            ownerUid = user.id,
            ownerEmail = normalizedEmail,
            ownerName = user.displayName,
            groupName = normalizedName,
        )
    )
}

data class MemberDoc(
    val path: String,
    val id: String,
    val data: Map<String, Any?>,
)

fun interface GroupLoader {
    suspend fun load(groupId: String): Group?
}

suspend fun assembleMemberships(
    docs: List<MemberDoc>,
    loader: GroupLoader,
): List<GroupMembership> {
    return docs.mapNotNull { doc ->
        val groupId = FirestorePaths.groupIdFromMemberPath(doc.path) ?: return@mapNotNull null
        val member = groupMemberFrom(groupId, doc.id, doc.data) ?: return@mapNotNull null
        val group = try {
            loader.load(groupId)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        } ?: return@mapNotNull null
        GroupMembership(group, member.role)
    }
}

fun mergeDevices(lists: List<List<HostDevice>>): List<HostDevice> {
    return lists.flatMap { it }
        .distinctBy { it.id }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayName })
}

private sealed interface GroupDevicesResult {
    data class Success(val devices: List<HostDevice>) : GroupDevicesResult
    data object Denied : GroupDevicesResult
}

fun combineGroupDevices(perGroup: List<Flow<List<HostDevice>>>): Flow<List<HostDevice>> {
    if (perGroup.isEmpty()) return flowOf(emptyList())

    val wrappedFlows = perGroup.map { flow ->
        flow.map<List<HostDevice>, GroupDevicesResult> { GroupDevicesResult.Success(it) }
            .catch { e ->
                if (e is GroupError.AccessLost) {
                    emit(GroupDevicesResult.Denied)
                } else {
                    throw e
                }
            }
    }

    return combine(wrappedFlows) { results ->
        val allDenied = results.all { it is GroupDevicesResult.Denied }
        if (allDenied) {
            throw GroupError.AccessLost
        }
        val successLists = results.mapNotNull { (it as? GroupDevicesResult.Success)?.devices }
        mergeDevices(successLists)
    }
}
