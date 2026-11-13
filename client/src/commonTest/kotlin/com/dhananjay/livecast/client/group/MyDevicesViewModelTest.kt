package com.dhananjay.livecast.client.group

import com.dhananjay.livecast.auth.AuthState
import com.dhananjay.livecast.auth.SessionProvider
import com.dhananjay.livecast.auth.User
import com.dhananjay.livecast.protocol.Group
import com.dhananjay.livecast.protocol.GroupMembership
import com.dhananjay.livecast.protocol.GroupRole
import com.dhananjay.livecast.protocol.GroupRules
import com.dhananjay.livecast.protocol.HostDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeMyDevicesSessionProvider(
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

private class FakeGroupRepository : GroupRepository {
    val membershipsFlow = MutableStateFlow<List<GroupMembership>>(emptyList())
    var observeMembershipsCalled = false
    var createGroupResult: Result<String> = Result.success("g1")

    override fun observeMyMemberships(): Flow<List<GroupMembership>> {
        observeMembershipsCalled = true
        return membershipsFlow
    }

    override suspend fun createGroup(name: String): Result<String> = createGroupResult
}

private class FakeGroupDeviceRepository : GroupDeviceRepository {
    val observedGroupIds = mutableListOf<List<String>>()
    val devicesFlow = MutableStateFlow<List<HostDevice>>(emptyList())
    var customFlow: Flow<List<HostDevice>>? = null
    val removedDeviceIds = mutableListOf<String>()
    var removeResult: Result<Unit> = Result.success(Unit)

    override fun observeDevices(groupIds: List<String>): Flow<List<HostDevice>> {
        observedGroupIds.add(groupIds)
        return customFlow ?: devicesFlow
    }

    override suspend fun removeFromGroup(deviceId: String): Result<Unit> {
        removedDeviceIds.add(deviceId)
        return removeResult
    }
}

private class FakePresenceClock(
    var now: Long = 1_000_000L,
) : PresenceClock {
    val tickFlow = MutableSharedFlow<Unit>()
    var requestedPeriodMs: Long? = null

    override fun nowEpochMs(): Long = now

    override fun ticks(periodMs: Long): Flow<Unit> {
        requestedPeriodMs = periodMs
        return tickFlow
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MyDevicesViewModelTest {

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
    fun guestSeesNoDevices() = runTest {
        val clock = FakePresenceClock()
        val auth = FakeMyDevicesSessionProvider(User("u1", null, null, isAnonymous = true))
        val groupsRepo = FakeGroupRepository()
        val devicesRepo = FakeGroupDeviceRepository()

        val viewModel = MyDevicesViewModel(auth, groupsRepo, devicesRepo, clock)
        runCurrent()

        assertTrue(viewModel.state.value.guest)
        assertFalse(groupsRepo.observeMembershipsCalled)
        assertEquals(emptyList(), viewModel.state.value.rows)
    }

    @Test
    fun listsDevicesOfViewableGroupsOnly() = runTest {
        val clock = FakePresenceClock()
        val auth = FakeMyDevicesSessionProvider(User("u1", "u1@x.com", "User 1", emailVerified = true))
        val groupsRepo = FakeGroupRepository()
        val devicesRepo = FakeGroupDeviceRepository()

        val g1 = Group("g1", "Group 1", "o1", 1000L)
        val g2 = Group("g2", "Group 2", "o2", 1000L)
        groupsRepo.membershipsFlow.value = listOf(
            GroupMembership(g1, GroupRole.SUPERVISOR),
            GroupMembership(g2, GroupRole.DEVICE)
        )

        val viewModel = MyDevicesViewModel(auth, groupsRepo, devicesRepo, clock)
        runCurrent()

        assertEquals(listOf(listOf("g1")), devicesRepo.observedGroupIds)
    }

    @Test
    fun onlineWithinTwoMinutes() = runTest {
        val clock = FakePresenceClock(now = 1_000_000L)
        val auth = FakeMyDevicesSessionProvider(User("u1", "u1@x.com", "User 1", emailVerified = true))
        val groupsRepo = FakeGroupRepository()
        val devicesRepo = FakeGroupDeviceRepository()

        val g1 = Group("g1", "Group 1", "u1", 1000L)
        groupsRepo.membershipsFlow.value = listOf(GroupMembership(g1, GroupRole.OWNER))

        val dev1 = HostDevice("d1", "Dev 1", "Model 1", online = true, lastSeenEpochMs = 880_000L, groupId = "g1")
        val dev2 = HostDevice("d2", "Dev 2", "Model 2", online = true, lastSeenEpochMs = 879_999L, groupId = "g1")
        val dev3 = HostDevice("d3", "Dev 3", "Model 3", online = false, lastSeenEpochMs = 1_000_000L, groupId = "g1")
        devicesRepo.devicesFlow.value = listOf(dev1, dev2, dev3)

        val viewModel = MyDevicesViewModel(
            session = auth,
            groups = groupsRepo,
            devices = devicesRepo,
            clock = clock,
        )
        runCurrent()

        val rows = viewModel.state.value.rows
        assertEquals(3, rows.size)
        val row1 = rows.first { it.device.id == "d1" }
        val row2 = rows.first { it.device.id == "d2" }
        val row3 = rows.first { it.device.id == "d3" }

        assertTrue(row1.online)
        assertFalse(row2.online)
        assertFalse(row3.online)
    }

    @Test
    fun staleDeviceGoesOfflineOnTick() = runTest {
        val clock = FakePresenceClock(now = 1_000_000L)
        val auth = FakeMyDevicesSessionProvider(User("u1", "u1@x.com", "User 1", emailVerified = true))
        val groupsRepo = FakeGroupRepository()
        val devicesRepo = FakeGroupDeviceRepository()

        val g1 = Group("g1", "Group 1", "u1", 1000L)
        groupsRepo.membershipsFlow.value = listOf(GroupMembership(g1, GroupRole.OWNER))

        val dev1 = HostDevice("d1", "Dev 1", "Model 1", online = true, lastSeenEpochMs = 1_000_000L, groupId = "g1")
        devicesRepo.devicesFlow.value = listOf(dev1)

        val viewModel = MyDevicesViewModel(
            session = auth,
            groups = groupsRepo,
            devices = devicesRepo,
            clock = clock,
        )
        runCurrent()

        assertEquals(GroupRules.PRESENCE_TICK_MS, clock.requestedPeriodMs)
        assertTrue(viewModel.state.value.rows.first().online)

        // Advance fake clock past 120_000 ms and emit one tick, with no snapshot change
        clock.now += 135_000L
        clock.tickFlow.emit(Unit)
        runCurrent()

        assertFalse(viewModel.state.value.rows.first().online)
    }

    @Test
    fun onlyOwnerCanRemove() = runTest {
        val clock = FakePresenceClock()
        val auth = FakeMyDevicesSessionProvider(User("u1", "u1@x.com", "User 1", emailVerified = true))
        val groupsRepo = FakeGroupRepository()
        val devicesRepo = FakeGroupDeviceRepository()

        val g1 = Group("g1", "Group 1", "u1", 1000L)
        val g3 = Group("g3", "Group 3", "other", 1000L)
        groupsRepo.membershipsFlow.value = listOf(
            GroupMembership(g1, GroupRole.OWNER),
            GroupMembership(g3, GroupRole.SUPERVISOR)
        )

        val dev1 = HostDevice("d1", "Dev 1", "Model 1", groupId = "g1")
        val dev2 = HostDevice("d2", "Dev 2", "Model 2", groupId = "g3")
        devicesRepo.devicesFlow.value = listOf(dev1, dev2)

        val viewModel = MyDevicesViewModel(auth, groupsRepo, devicesRepo, clock)
        runCurrent()

        val row1 = viewModel.state.value.rows.first { it.device.id == "d1" }
        val row2 = viewModel.state.value.rows.first { it.device.id == "d2" }
        assertTrue(row1.canRemove)
        assertFalse(row2.canRemove)

        // Trying to remove non-removable device d2 does nothing
        viewModel.remove("d2")
        runCurrent()
        assertEquals(emptyList(), devicesRepo.removedDeviceIds)

        // Removing removable device d1 calls repo
        viewModel.remove("d1")
        runCurrent()
        assertEquals(listOf("d1"), devicesRepo.removedDeviceIds)
    }

    @Test
    fun accessLostShowsMessage() = runTest {
        val clock = FakePresenceClock()
        val auth = FakeMyDevicesSessionProvider(User("u1", "u1@x.com", "User 1", emailVerified = true))
        val groupsRepo = FakeGroupRepository()
        val devicesRepo = FakeGroupDeviceRepository()

        val g1 = Group("g1", "Group 1", "u1", 1000L)
        groupsRepo.membershipsFlow.value = listOf(GroupMembership(g1, GroupRole.OWNER))
        devicesRepo.customFlow = flow { throw GroupError.AccessLost }

        val viewModel = MyDevicesViewModel(auth, groupsRepo, devicesRepo, clock)
        runCurrent()

        assertEquals("You no longer have access", viewModel.state.value.error)
        assertEquals(emptyList(), viewModel.state.value.rows)
    }

    @Test
    fun userSwitchResetsDevices() = runTest {
        val clock = FakePresenceClock()
        val userA = User("u1", "u1@x.com", "User 1", emailVerified = true)
        val userB = User("u2", null, null, isAnonymous = true)
        val auth = FakeMyDevicesSessionProvider(userA)
        val groupsRepo = FakeGroupRepository()
        val devicesRepo = FakeGroupDeviceRepository()

        val g1 = Group("g1", "Group 1", "u1", 1000L)
        groupsRepo.membershipsFlow.value = listOf(GroupMembership(g1, GroupRole.OWNER))
        val dev1 = HostDevice("d1", "Dev 1", "Model 1", groupId = "g1")
        devicesRepo.devicesFlow.value = listOf(dev1)

        val viewModel = MyDevicesViewModel(auth, groupsRepo, devicesRepo, clock)
        runCurrent()

        assertEquals(1, viewModel.state.value.rows.size)

        auth.setUser(userB)
        runCurrent()

        assertTrue(viewModel.state.value.guest)
        assertEquals(emptyList(), viewModel.state.value.rows)
    }

    @Test
    fun removeFailureShowsError() = runTest {
        val clock = FakePresenceClock()
        val auth = FakeMyDevicesSessionProvider(User("u1", "u1@x.com", "User 1", emailVerified = true))
        val groupsRepo = FakeGroupRepository()
        val devicesRepo = FakeGroupDeviceRepository().apply {
            removeResult = Result.failure(IllegalStateException("Failed to delete device"))
        }

        val g1 = Group("g1", "Group 1", "u1", 1000L)
        groupsRepo.membershipsFlow.value = listOf(GroupMembership(g1, GroupRole.OWNER))
        val dev1 = HostDevice("d1", "Dev 1", "Model 1", groupId = "g1")
        devicesRepo.devicesFlow.value = listOf(dev1)

        val viewModel = MyDevicesViewModel(auth, groupsRepo, devicesRepo, clock)
        runCurrent()

        viewModel.remove("d1")
        runCurrent()

        assertEquals("Failed to delete device", viewModel.state.value.error)
    }
}
