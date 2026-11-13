package com.dhananjay.livecast.client.group

import com.dhananjay.livecast.protocol.GroupMember
import com.dhananjay.livecast.protocol.GroupMembership
import com.dhananjay.livecast.protocol.GroupRole
import com.dhananjay.livecast.protocol.HostDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface GroupRepository {
    fun observeMyMemberships(): Flow<List<GroupMembership>>
    suspend fun createGroup(name: String): Result<String>
}

interface GroupMemberRepository {
    fun observeMembers(groupId: String): Flow<List<GroupMember>>
    suspend fun addMember(groupId: String, email: String, role: GroupRole, name: String?): Result<Unit>
    suspend fun changeRole(groupId: String, email: String, role: GroupRole): Result<Unit>
    suspend fun removeMember(groupId: String, email: String): Result<Unit>
}

interface GroupDeviceRepository {
    fun observeDevices(groupIds: List<String>): Flow<List<HostDevice>>
    suspend fun removeFromGroup(deviceId: String): Result<Unit>
}

sealed class GroupError(message: String) : Exception(message) {
    data object AccessLost : GroupError("You no longer have access")
    data object NotSignedIn : GroupError("Not signed in")
    data object EmailNotVerified : GroupError("Verify your email to continue")
    class AlreadyMember(email: String) : GroupError("$email is already in this group")
    open class Unsupported(message: String = "Groups are not available on this platform yet") : GroupError(message)
}

class UnsupportedGroupRepository(
    private val message: String = "Groups are not available on this platform yet"
) : GroupRepository {
    override fun observeMyMemberships(): Flow<List<GroupMembership>> = flowOf(emptyList())
    override suspend fun createGroup(name: String): Result<String> =
        Result.failure(GroupError.Unsupported(message))
}

class UnsupportedGroupMemberRepository(
    private val message: String = "Groups are not available on this platform yet"
) : GroupMemberRepository {
    override fun observeMembers(groupId: String): Flow<List<GroupMember>> = flowOf(emptyList())
    override suspend fun addMember(groupId: String, email: String, role: GroupRole, name: String?): Result<Unit> =
        Result.failure(GroupError.Unsupported(message))
    override suspend fun changeRole(groupId: String, email: String, role: GroupRole): Result<Unit> =
        Result.failure(GroupError.Unsupported(message))
    override suspend fun removeMember(groupId: String, email: String): Result<Unit> =
        Result.failure(GroupError.Unsupported(message))
}

class UnsupportedGroupDeviceRepository(
    private val message: String = "Groups are not available on this platform yet"
) : GroupDeviceRepository {
    override fun observeDevices(groupIds: List<String>): Flow<List<HostDevice>> = flowOf(emptyList())
    override suspend fun removeFromGroup(deviceId: String): Result<Unit> =
        Result.failure(GroupError.Unsupported(message))
}
