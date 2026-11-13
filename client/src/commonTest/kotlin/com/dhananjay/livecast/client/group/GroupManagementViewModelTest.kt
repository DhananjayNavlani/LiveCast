package com.dhananjay.livecast.client.group

import com.dhananjay.livecast.auth.AuthState
import com.dhananjay.livecast.auth.SessionProvider
import com.dhananjay.livecast.auth.User
import com.dhananjay.livecast.protocol.Group
import com.dhananjay.livecast.protocol.GroupMember
import com.dhananjay.livecast.protocol.GroupMembership
import com.dhananjay.livecast.protocol.GroupRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class FakeGroupMgmtSessionProvider(
    override var currentUser: User? = null
) : SessionProvider {
    private val _authState = MutableStateFlow<AuthState>(
        currentUser?.let { AuthState.Authenticated(it) } ?: AuthState.Unauthenticated
    )
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    fun setUser(user: User?) {
        currentUser = user
        _authState.value = user?.let { AuthState.Authenticated(it) } ?: AuthState.Unauthenticated
    }

    override suspend fun getIdToken(forceRefresh: Boolean): String? = "fake-token"
}

private class FakeGroupMgmtGroupRepo : GroupRepository {
    val membershipsFlow = MutableStateFlow<List<GroupMembership>>(emptyList())
    val createdGroupNames = mutableListOf<String>()
    var createGroupResult: Result<String> = Result.success("new-g-id")
    var createGroupDelayMs = 0L

    override fun observeMyMemberships(): Flow<List<GroupMembership>> = membershipsFlow

    override suspend fun createGroup(name: String): Result<String> {
        createdGroupNames.add(name)
        if (createGroupDelayMs > 0) delay(createGroupDelayMs)
        return createGroupResult
    }
}

private class FakeGroupMgmtMemberRepo : GroupMemberRepository {
    val observedGroupIds = mutableListOf<String>()
    val membersFlow = MutableStateFlow<List<GroupMember>>(emptyList())

    data class AddMemberCall(val groupId: String, val email: String, val role: GroupRole, val name: String?)
    val addedMembers = mutableListOf<AddMemberCall>()
    var addMemberResult: Result<Unit> = Result.success(Unit)

    data class ChangeRoleCall(val groupId: String, val email: String, val role: GroupRole)
    val changedRoles = mutableListOf<ChangeRoleCall>()
    var changeRoleResult: Result<Unit> = Result.success(Unit)

    data class RemoveMemberCall(val groupId: String, val email: String)
    val removedMembers = mutableListOf<RemoveMemberCall>()
    var removeMemberResult: Result<Unit> = Result.success(Unit)

    override fun observeMembers(groupId: String): Flow<List<GroupMember>> {
        observedGroupIds.add(groupId)
        return membersFlow
    }

    override suspend fun addMember(groupId: String, email: String, role: GroupRole, name: String?): Result<Unit> {
        addedMembers.add(AddMemberCall(groupId, email, role, name))
        return addMemberResult
    }

    override suspend fun changeRole(groupId: String, email: String, role: GroupRole): Result<Unit> {
        changedRoles.add(ChangeRoleCall(groupId, email, role))
        return changeRoleResult
    }

    override suspend fun removeMember(groupId: String, email: String): Result<Unit> {
        removedMembers.add(RemoveMemberCall(groupId, email))
        return removeMemberResult
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class GroupManagementViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun createGroupTrimsNameAndCreatesOnce() = runTest {
        val user = User("u1", "owner@x.com", "Owner", emailVerified = true)
        val session = FakeGroupMgmtSessionProvider(user)
        val groupsRepo = FakeGroupMgmtGroupRepo().apply { createGroupDelayMs = 500L }
        val memberRepo = FakeGroupMgmtMemberRepo()
        val viewModel = GroupManagementViewModel(session, groupsRepo, memberRepo)
        runCurrent()

        viewModel.createGroup("  Family  ")
        viewModel.createGroup("  Family  ")
        advanceUntilIdle()

        assertEquals(listOf("Family"), groupsRepo.createdGroupNames)
    }

    @Test
    fun blankGroupNameRejected() = runTest {
        val user = User("u1", "owner@x.com", "Owner", emailVerified = true)
        val session = FakeGroupMgmtSessionProvider(user)
        val groupsRepo = FakeGroupMgmtGroupRepo()
        val memberRepo = FakeGroupMgmtMemberRepo()
        val viewModel = GroupManagementViewModel(session, groupsRepo, memberRepo)
        runCurrent()

        viewModel.createGroup("   ")
        runCurrent()

        assertEquals(emptyList(), groupsRepo.createdGroupNames)
        assertEquals("Enter a group name (up to 60 characters)", viewModel.state.value.error)
    }

    @Test
    fun addMemberNormalizesEmail() = runTest {
        val user = User("u1", "owner@x.com", "Owner", emailVerified = true)
        val session = FakeGroupMgmtSessionProvider(user)
        val groupsRepo = FakeGroupMgmtGroupRepo()
        val memberRepo = FakeGroupMgmtMemberRepo()

        val g1 = Group("g1", "Group 1", "u1", 1000L)
        groupsRepo.membershipsFlow.value = listOf(GroupMembership(g1, GroupRole.OWNER))

        val viewModel = GroupManagementViewModel(session, groupsRepo, memberRepo)
        runCurrent()

        viewModel.addMember("  Kid@Example.COM ", GroupRole.DEVICE, null)
        advanceUntilIdle()

        assertEquals(1, memberRepo.addedMembers.size)
        val call = memberRepo.addedMembers.first()
        assertEquals("g1", call.groupId)
        assertEquals("kid@example.com", call.email)
        assertEquals(GroupRole.DEVICE, call.role)
    }

    @Test
    fun invalidEmailRejected() = runTest {
        val user = User("u1", "owner@x.com", "Owner", emailVerified = true)
        val session = FakeGroupMgmtSessionProvider(user)
        val groupsRepo = FakeGroupMgmtGroupRepo()
        val memberRepo = FakeGroupMgmtMemberRepo()

        val g1 = Group("g1", "Group 1", "u1", 1000L)
        groupsRepo.membershipsFlow.value = listOf(GroupMembership(g1, GroupRole.OWNER))

        val viewModel = GroupManagementViewModel(session, groupsRepo, memberRepo)
        runCurrent()

        viewModel.addMember("invalid-email", GroupRole.DEVICE, null)
        runCurrent()

        assertEquals(emptyList(), memberRepo.addedMembers)
        assertEquals("Enter a valid email", viewModel.state.value.error)
    }

    @Test
    fun nonOwnerCannotManage() = runTest {
        val user = User("u1", "sup@x.com", "Supervisor", emailVerified = true)
        val session = FakeGroupMgmtSessionProvider(user)
        val groupsRepo = FakeGroupMgmtGroupRepo()
        val memberRepo = FakeGroupMgmtMemberRepo()

        val g1 = Group("g1", "Group 1", "other", 1000L)
        groupsRepo.membershipsFlow.value = listOf(GroupMembership(g1, GroupRole.SUPERVISOR))

        val viewModel = GroupManagementViewModel(session, groupsRepo, memberRepo)
        runCurrent()

        assertFalse(viewModel.state.value.canManage)

        viewModel.addMember("kid@x.com", GroupRole.DEVICE, null)
        viewModel.removeMember("other@x.com")
        viewModel.changeRole("other@x.com", GroupRole.DEVICE)
        runCurrent()

        assertEquals(emptyList(), memberRepo.addedMembers)
        assertEquals(emptyList(), memberRepo.removedMembers)
        assertEquals(emptyList(), memberRepo.changedRoles)
    }

    @Test
    fun ownerCannotRemoveSelf() = runTest {
        val user = User("u1", "Owner@X.com", "Owner", emailVerified = true)
        val session = FakeGroupMgmtSessionProvider(user)
        val groupsRepo = FakeGroupMgmtGroupRepo()
        val memberRepo = FakeGroupMgmtMemberRepo()

        val g1 = Group("g1", "Group 1", "u1", 1000L)
        groupsRepo.membershipsFlow.value = listOf(GroupMembership(g1, GroupRole.OWNER))

        val viewModel = GroupManagementViewModel(session, groupsRepo, memberRepo)
        runCurrent()

        viewModel.removeMember("owner@x.com")
        runCurrent()

        assertEquals(emptyList(), memberRepo.removedMembers)
        assertEquals("You can't remove or demote yourself", viewModel.state.value.error)
    }

    @Test
    fun ownerCannotDemoteSelf() = runTest {
        val user = User("u1", "Owner@X.com", "Owner", emailVerified = true)
        val session = FakeGroupMgmtSessionProvider(user)
        val groupsRepo = FakeGroupMgmtGroupRepo()
        val memberRepo = FakeGroupMgmtMemberRepo()

        val g1 = Group("g1", "Group 1", "u1", 1000L)
        groupsRepo.membershipsFlow.value = listOf(GroupMembership(g1, GroupRole.OWNER))

        val viewModel = GroupManagementViewModel(session, groupsRepo, memberRepo)
        runCurrent()

        viewModel.changeRole("owner@x.com", GroupRole.SUPERVISOR)
        runCurrent()

        assertEquals(emptyList(), memberRepo.changedRoles)
        assertEquals("You can't remove or demote yourself", viewModel.state.value.error)
    }

    @Test
    fun alreadyMemberShowsMessage() = runTest {
        val user = User("u1", "owner@x.com", "Owner", emailVerified = true)
        val session = FakeGroupMgmtSessionProvider(user)
        val groupsRepo = FakeGroupMgmtGroupRepo()
        val memberRepo = FakeGroupMgmtMemberRepo().apply {
            addMemberResult = Result.failure(GroupError.AlreadyMember("kid@x.com"))
        }

        val g1 = Group("g1", "Group 1", "u1", 1000L)
        groupsRepo.membershipsFlow.value = listOf(GroupMembership(g1, GroupRole.OWNER))

        val viewModel = GroupManagementViewModel(session, groupsRepo, memberRepo)
        runCurrent()

        viewModel.addMember("kid@x.com", GroupRole.DEVICE, null)
        advanceUntilIdle()

        assertEquals("kid@x.com is already in this group", viewModel.state.value.error)
    }

    @Test
    fun selectGroupSwitchesMembersListener() = runTest {
        val user = User("u1", "owner@x.com", "Owner", emailVerified = true)
        val session = FakeGroupMgmtSessionProvider(user)
        val groupsRepo = FakeGroupMgmtGroupRepo()
        val memberRepo = FakeGroupMgmtMemberRepo()

        val g1 = Group("g1", "Group 1", "u1", 1000L)
        val g2 = Group("g2", "Group 2", "u1", 1000L)
        groupsRepo.membershipsFlow.value = listOf(
            GroupMembership(g1, GroupRole.OWNER),
            GroupMembership(g2, GroupRole.OWNER)
        )

        val viewModel = GroupManagementViewModel(session, groupsRepo, memberRepo)
        runCurrent()

        assertEquals("g1", viewModel.state.value.selectedGroupId)
        assertEquals(listOf("g1"), memberRepo.observedGroupIds)

        viewModel.selectGroup("g2")
        runCurrent()

        assertEquals("g2", viewModel.state.value.selectedGroupId)
        assertEquals(listOf("g1", "g2"), memberRepo.observedGroupIds)
    }
}
