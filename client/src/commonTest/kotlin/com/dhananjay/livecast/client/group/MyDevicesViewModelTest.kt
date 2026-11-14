package com.dhananjay.livecast.client.group

import androidx.lifecycle.viewModelScope
import com.dhananjay.livecast.auth.AuthState
import com.dhananjay.livecast.auth.SessionProvider
import com.dhananjay.livecast.auth.User
import com.dhananjay.livecast.client.check.CheckRequestResult
import com.dhananjay.livecast.client.check.CheckState
import com.dhananjay.livecast.client.check.DeviceCheckService
import com.dhananjay.livecast.protocol.Group
import com.dhananjay.livecast.protocol.GroupMembership
import com.dhananjay.livecast.protocol.GroupRole
import com.dhananjay.livecast.protocol.HostDevice
import com.dhananjay.livecast.protocol.ToggleState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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

private class FakeDeviceCheckService(
    var result: CheckRequestResult = CheckRequestResult.Requested("test-check-id")
) : DeviceCheckService {
    val requestedDevices = mutableListOf<String>()

    override suspend fun requestCheck(deviceId: String): CheckRequestResult {
        requestedDevices.add(deviceId)
        return result
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
        val checkService = FakeDeviceCheckService()
        val auth = FakeMyDevicesSessionProvider(User("u1", null, null, isAnonymous = true))
        val groupsRepo = FakeGroupRepository()
        val devicesRepo = FakeGroupDeviceRepository()

        val viewModel = MyDevicesViewModel(auth, groupsRepo, devicesRepo, checkService)
        runCurrent()

        assertTrue(viewModel.state.value.guest)
        assertFalse(groupsRepo.observeMembershipsCalled)
        assertEquals(emptyList(), viewModel.state.value.rows)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun listsDevicesOfViewableGroupsOnly() = runTest {
        val checkService = FakeDeviceCheckService()
        val auth = FakeMyDevicesSessionProvider(User("u1", "u1@x.com", "User 1", emailVerified = true))
        val groupsRepo = FakeGroupRepository()
        val devicesRepo = FakeGroupDeviceRepository()

        val g1 = Group("g1", "Group 1", "o1", 1000L)
        val g2 = Group("g2", "Group 2", "o2", 1000L)
        groupsRepo.membershipsFlow.value = listOf(
            GroupMembership(g1, GroupRole.SUPERVISOR),
            GroupMembership(g2, GroupRole.DEVICE)
        )

        val viewModel = MyDevicesViewModel(auth, groupsRepo, devicesRepo, checkService)
        runCurrent()

        assertEquals(listOf(listOf("g1")), devicesRepo.observedGroupIds)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun checkOnlineWhenReplyArrives() = runTest {
        val auth = FakeMyDevicesSessionProvider(User("u1", "u1@x.com", "User 1", emailVerified = true))
        val groupsRepo = FakeGroupRepository()
        val devicesRepo = FakeGroupDeviceRepository()
        val checkService = FakeDeviceCheckService(CheckRequestResult.Requested("chk-1"))

        val g1 = Group("g1", "Group 1", "u1", 1000L)
        groupsRepo.membershipsFlow.value = listOf(GroupMembership(g1, GroupRole.OWNER))
        val dev1 = HostDevice("d1", "Dev 1", "Model 1", groupId = "g1")
        devicesRepo.devicesFlow.value = listOf(dev1)

        val viewModel = MyDevicesViewModel(auth, groupsRepo, devicesRepo, checkService)
        runCurrent()

        viewModel.check("d1")
        runCurrent()

        assertEquals(CheckState.Checking, viewModel.state.value.rows.first().check)

        val updatedDev = dev1.copy(lastCheckId = "chk-1", lastCheckAtEpochMs = 123456L)
        devicesRepo.devicesFlow.value = listOf(updatedDev)
        runCurrent()

        assertEquals(CheckState.Online(123456L), viewModel.state.value.rows.first().check)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun checkOfflineAfterTimeout() = runTest {
        val auth = FakeMyDevicesSessionProvider(User("u1", "u1@x.com", "User 1", emailVerified = true))
        val groupsRepo = FakeGroupRepository()
        val devicesRepo = FakeGroupDeviceRepository()
        val checkService = FakeDeviceCheckService(CheckRequestResult.Requested("chk-1"))

        val g1 = Group("g1", "Group 1", "u1", 1000L)
        groupsRepo.membershipsFlow.value = listOf(GroupMembership(g1, GroupRole.OWNER))
        val dev1 = HostDevice("d1", "Dev 1", "Model 1", groupId = "g1")
        devicesRepo.devicesFlow.value = listOf(dev1)

        val viewModel = MyDevicesViewModel(auth, groupsRepo, devicesRepo, checkService)
        runCurrent()

        viewModel.check("d1")
        runCurrent()

        assertEquals(CheckState.Checking, viewModel.state.value.rows.first().check)

        advanceTimeBy(20_001L)
        runCurrent()

        assertEquals(CheckState.Offline, viewModel.state.value.rows.first().check)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun checkAppRemoved() = runTest {
        val auth = FakeMyDevicesSessionProvider(User("u1", "u1@x.com", "User 1", emailVerified = true))
        val groupsRepo = FakeGroupRepository()
        val devicesRepo = FakeGroupDeviceRepository()
        val checkService = FakeDeviceCheckService(CheckRequestResult.AppRemoved)

        val g1 = Group("g1", "Group 1", "u1", 1000L)
        groupsRepo.membershipsFlow.value = listOf(GroupMembership(g1, GroupRole.OWNER))
        val dev1 = HostDevice("d1", "Dev 1", "Model 1", groupId = "g1")
        devicesRepo.devicesFlow.value = listOf(dev1)

        val viewModel = MyDevicesViewModel(auth, groupsRepo, devicesRepo, checkService)
        runCurrent()

        viewModel.check("d1")
        runCurrent()

        assertEquals(CheckState.AppRemoved, viewModel.state.value.rows.first().check)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun checkNotSetUp() = runTest {
        val auth = FakeMyDevicesSessionProvider(User("u1", "u1@x.com", "User 1", emailVerified = true))
        val groupsRepo = FakeGroupRepository()
        val devicesRepo = FakeGroupDeviceRepository()
        val checkService = FakeDeviceCheckService(CheckRequestResult.NoPushToken)

        val g1 = Group("g1", "Group 1", "u1", 1000L)
        groupsRepo.membershipsFlow.value = listOf(GroupMembership(g1, GroupRole.OWNER))
        val dev1 = HostDevice("d1", "Dev 1", "Model 1", groupId = "g1")
        devicesRepo.devicesFlow.value = listOf(dev1)

        val viewModel = MyDevicesViewModel(auth, groupsRepo, devicesRepo, checkService)
        runCurrent()

        viewModel.check("d1")
        runCurrent()

        assertEquals(CheckState.NotSetUp, viewModel.state.value.rows.first().check)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun checkRateLimited() = runTest {
        val auth = FakeMyDevicesSessionProvider(User("u1", "u1@x.com", "User 1", emailVerified = true))
        val groupsRepo = FakeGroupRepository()
        val devicesRepo = FakeGroupDeviceRepository()
        val checkService = FakeDeviceCheckService(CheckRequestResult.RateLimited)

        val g1 = Group("g1", "Group 1", "u1", 1000L)
        groupsRepo.membershipsFlow.value = listOf(GroupMembership(g1, GroupRole.OWNER))
        val dev1 = HostDevice("d1", "Dev 1", "Model 1", groupId = "g1")
        devicesRepo.devicesFlow.value = listOf(dev1)

        val viewModel = MyDevicesViewModel(auth, groupsRepo, devicesRepo, checkService)
        runCurrent()

        viewModel.check("d1")
        runCurrent()

        assertEquals(CheckState.RateLimited, viewModel.state.value.rows.first().check)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun doubleTapIgnoredWhileChecking() = runTest {
        val auth = FakeMyDevicesSessionProvider(User("u1", "u1@x.com", "User 1", emailVerified = true))
        val groupsRepo = FakeGroupRepository()
        val devicesRepo = FakeGroupDeviceRepository()
        val checkService = FakeDeviceCheckService(CheckRequestResult.Requested("chk-1"))

        val g1 = Group("g1", "Group 1", "u1", 1000L)
        groupsRepo.membershipsFlow.value = listOf(GroupMembership(g1, GroupRole.OWNER))
        val dev1 = HostDevice("d1", "Dev 1", "Model 1", groupId = "g1")
        devicesRepo.devicesFlow.value = listOf(dev1)

        val viewModel = MyDevicesViewModel(auth, groupsRepo, devicesRepo, checkService)
        runCurrent()

        viewModel.check("d1")
        runCurrent()
        assertEquals(CheckState.Checking, viewModel.state.value.rows.first().check)
        assertEquals(1, checkService.requestedDevices.size)

        viewModel.check("d1")
        runCurrent()
        assertEquals(1, checkService.requestedDevices.size)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun userSwitchClearsCheckState() = runTest {
        val auth = FakeMyDevicesSessionProvider(User("u1", "u1@x.com", "User 1", emailVerified = true))
        val groupsRepo = FakeGroupRepository()
        val devicesRepo = FakeGroupDeviceRepository()
        val checkService = FakeDeviceCheckService(CheckRequestResult.AppRemoved)

        val g1 = Group("g1", "Group 1", "u1", 1000L)
        groupsRepo.membershipsFlow.value = listOf(GroupMembership(g1, GroupRole.OWNER))
        val dev1 = HostDevice("d1", "Dev 1", "Model 1", groupId = "g1")
        devicesRepo.devicesFlow.value = listOf(dev1)

        val viewModel = MyDevicesViewModel(auth, groupsRepo, devicesRepo, checkService)
        runCurrent()

        viewModel.check("d1")
        runCurrent()
        assertEquals(CheckState.AppRemoved, viewModel.state.value.rows.first().check)

        auth.setUser(User("u2", "u2@x.com", "User 2", emailVerified = true))
        runCurrent()
        runCurrent()

        val rows = viewModel.state.value.rows
        assertEquals(1, rows.size)
        rows.forEach { assertEquals(CheckState.Idle, it.check) }
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun userSwitchCancelsInFlightCheck() = runTest {
        val auth = FakeMyDevicesSessionProvider(User("u1", "u1@x.com", "User 1", emailVerified = true))
        val groupsRepo = FakeGroupRepository()
        val devicesRepo = FakeGroupDeviceRepository()
        val checkService = FakeDeviceCheckService(CheckRequestResult.Requested("c1"))

        val g1 = Group("g1", "Group 1", "u1", 1000L)
        groupsRepo.membershipsFlow.value = listOf(GroupMembership(g1, GroupRole.OWNER))
        val dev1 = HostDevice("d1", "Dev 1", "Model 1", groupId = "g1")
        devicesRepo.devicesFlow.value = listOf(dev1)

        val viewModel = MyDevicesViewModel(auth, groupsRepo, devicesRepo, checkService)
        runCurrent()

        viewModel.check("d1")
        runCurrent()
        assertEquals(CheckState.Checking, viewModel.state.value.rows.first().check)

        auth.setUser(User("u2", "u2@x.com", "User 2", emailVerified = true))
        runCurrent()
        runCurrent()

        advanceTimeBy(20_001L)
        runCurrent()

        val rows = viewModel.state.value.rows
        assertEquals(1, rows.size)
        assertEquals(CheckState.Idle, rows.first().check)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun replyBeforeWaitStartsIsOnline() = runTest {
        val auth = FakeMyDevicesSessionProvider(User("u1", "u1@x.com", "User 1", emailVerified = true))
        val groupsRepo = FakeGroupRepository()
        val devicesRepo = FakeGroupDeviceRepository()

        val g1 = Group("g1", "Group 1", "u1", 1000L)
        groupsRepo.membershipsFlow.value = listOf(GroupMembership(g1, GroupRole.OWNER))
        val dev1 = HostDevice("d1", "Dev 1", "Model 1", groupId = "g1")
        devicesRepo.devicesFlow.value = listOf(dev1)

        val checkService = object : DeviceCheckService {
            override suspend fun requestCheck(deviceId: String): CheckRequestResult {
                devicesRepo.devicesFlow.value = listOf(
                    dev1.copy(lastCheckId = "c-fast", lastCheckAtEpochMs = 7777L)
                )
                return CheckRequestResult.Requested("c-fast")
            }
        }

        val viewModel = MyDevicesViewModel(auth, groupsRepo, devicesRepo, checkService)
        runCurrent()

        viewModel.check("d1")
        runCurrent()

        assertEquals(CheckState.Online(7777L), viewModel.state.value.rows.first().check)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun engineCancellationEndsInError() = runTest {
        val auth = FakeMyDevicesSessionProvider(User("u1", "u1@x.com", "User 1", emailVerified = true))
        val groupsRepo = FakeGroupRepository()
        val devicesRepo = FakeGroupDeviceRepository()

        var callCount = 0
        val checkService = object : DeviceCheckService {
            override suspend fun requestCheck(deviceId: String): CheckRequestResult {
                callCount++
                if (callCount == 1) {
                    throw kotlinx.coroutines.CancellationException("engine cancelled")
                }
                return CheckRequestResult.Requested("c2")
            }
        }

        val g1 = Group("g1", "Group 1", "u1", 1000L)
        groupsRepo.membershipsFlow.value = listOf(GroupMembership(g1, GroupRole.OWNER))
        val dev1 = HostDevice("d1", "Dev 1", "Model 1", groupId = "g1")
        devicesRepo.devicesFlow.value = listOf(dev1)

        val viewModel = MyDevicesViewModel(auth, groupsRepo, devicesRepo, checkService)
        runCurrent()

        viewModel.check("d1")
        runCurrent()

        assertEquals(1, callCount)
        assertEquals(CheckState.Error("Check failed"), viewModel.state.value.rows.first().check)

        // Second tap calls the service again
        viewModel.check("d1")
        runCurrent()

        assertEquals(2, callCount)
        assertEquals(CheckState.Checking, viewModel.state.value.rows.first().check)

        viewModel.viewModelScope.cancel()
    }

    @Test
    fun rowCarriesAccessibilityAndNotifications() = runTest {
        val auth = FakeMyDevicesSessionProvider(User("u1", "u1@x.com", "User 1", emailVerified = true))
        val groupsRepo = FakeGroupRepository()
        val devicesRepo = FakeGroupDeviceRepository()
        val checkService = FakeDeviceCheckService()

        val g1 = Group("g1", "Group 1", "u1", 1000L)
        groupsRepo.membershipsFlow.value = listOf(GroupMembership(g1, GroupRole.OWNER))
        val dev1 = HostDevice(
            id = "d1",
            displayName = "Dev 1",
            model = "Model 1",
            groupId = "g1",
            accessibility = ToggleState.ON,
            notifications = ToggleState.OFF,
            statusAtEpochMs = 5000L,
        )
        devicesRepo.devicesFlow.value = listOf(dev1)

        val viewModel = MyDevicesViewModel(auth, groupsRepo, devicesRepo, checkService)
        runCurrent()

        val row = viewModel.state.value.rows.first()
        assertEquals(ToggleState.ON, row.accessibility)
        assertEquals(ToggleState.OFF, row.notifications)
        assertEquals(5000L, row.statusAtEpochMs)
        assertEquals(CheckState.Idle, row.check)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun onlyOwnerCanRemove() = runTest {
        val checkService = FakeDeviceCheckService()
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

        val viewModel = MyDevicesViewModel(auth, groupsRepo, devicesRepo, checkService)
        runCurrent()

        val row1 = viewModel.state.value.rows.first { it.device.id == "d1" }
        val row2 = viewModel.state.value.rows.first { it.device.id == "d2" }
        assertTrue(row1.canRemove)
        assertFalse(row2.canRemove)

        viewModel.remove("d2")
        runCurrent()
        assertEquals(emptyList(), devicesRepo.removedDeviceIds)

        viewModel.remove("d1")
        runCurrent()
        assertEquals(listOf("d1"), devicesRepo.removedDeviceIds)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun accessLostShowsMessage() = runTest {
        val checkService = FakeDeviceCheckService()
        val auth = FakeMyDevicesSessionProvider(User("u1", "u1@x.com", "User 1", emailVerified = true))
        val groupsRepo = FakeGroupRepository()
        val devicesRepo = FakeGroupDeviceRepository()

        val g1 = Group("g1", "Group 1", "u1", 1000L)
        groupsRepo.membershipsFlow.value = listOf(GroupMembership(g1, GroupRole.OWNER))

        val stream = Channel<Any>()
        devicesRepo.customFlow = flow {
            for (item in stream) {
                if (item is Throwable) throw item
                if (item is List<*>) @Suppress("UNCHECKED_CAST") emit(item as List<HostDevice>)
            }
        }

        val viewModel = MyDevicesViewModel(auth, groupsRepo, devicesRepo, checkService)
        runCurrent()

        val dev1 = HostDevice("d1", "Dev 1", "Model 1", groupId = "g1")
        stream.send(listOf(dev1))
        runCurrent()

        assertEquals(1, viewModel.state.value.rows.size)
        assertNull(viewModel.state.value.error)

        stream.send(GroupError.AccessLost)
        runCurrent()

        assertEquals("You no longer have access", viewModel.state.value.error)
        assertEquals(emptyList(), viewModel.state.value.rows)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun userSwitchResetsDevices() = runTest {
        val checkService = FakeDeviceCheckService()
        val userA = User("u1", "u1@x.com", "User 1", emailVerified = true)
        val userB = User("u2", null, null, isAnonymous = true)
        val auth = FakeMyDevicesSessionProvider(userA)
        val groupsRepo = FakeGroupRepository()
        val devicesRepo = FakeGroupDeviceRepository()

        val g1 = Group("g1", "Group 1", "u1", 1000L)
        groupsRepo.membershipsFlow.value = listOf(GroupMembership(g1, GroupRole.OWNER))
        val dev1 = HostDevice("d1", "Dev 1", "Model 1", groupId = "g1")
        devicesRepo.devicesFlow.value = listOf(dev1)

        val viewModel = MyDevicesViewModel(auth, groupsRepo, devicesRepo, checkService)
        runCurrent()

        assertEquals(1, viewModel.state.value.rows.size)

        auth.setUser(userB)
        runCurrent()

        assertTrue(viewModel.state.value.guest)
        assertEquals(emptyList(), viewModel.state.value.rows)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun verifiedUserSwitchResetsDevices() = runTest {
        val checkService = FakeDeviceCheckService()
        val userA = User("u1", "a@x.com", "User A", emailVerified = true)
        val userB = User("u2", "b@x.com", "User B", emailVerified = true)
        val auth = FakeMyDevicesSessionProvider(userA)
        val groupsRepo = FakeGroupRepository()
        val devicesRepo = FakeGroupDeviceRepository()

        val g1 = Group("g1", "Group 1", "u1", 1000L)
        groupsRepo.membershipsFlow.value = listOf(GroupMembership(g1, GroupRole.OWNER))
        val dev1 = HostDevice("d1", "Dev 1", "Model 1", groupId = "g1")
        devicesRepo.devicesFlow.value = listOf(dev1)

        val viewModel = MyDevicesViewModel(auth, groupsRepo, devicesRepo, checkService)
        runCurrent()

        assertEquals(1, viewModel.state.value.rows.size)
        val initialObserveCount = devicesRepo.observedGroupIds.size

        auth.setUser(userB)
        runCurrent()

        assertTrue(devicesRepo.observedGroupIds.size > initialObserveCount)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun sameViewableIdsDoNotResubscribe() = runTest {
        val checkService = FakeDeviceCheckService()
        val auth = FakeMyDevicesSessionProvider(User("u1", "u1@x.com", "User 1", emailVerified = true))
        val groupsRepo = FakeGroupRepository()
        val devicesRepo = FakeGroupDeviceRepository()

        val g1 = Group("g1", "Group 1", "u1", 1000L)
        groupsRepo.membershipsFlow.value = listOf(GroupMembership(g1, GroupRole.OWNER))
        val dev1 = HostDevice("d1", "Dev 1", "Model 1", groupId = "g1")
        devicesRepo.devicesFlow.value = listOf(dev1)

        val viewModel = MyDevicesViewModel(auth, groupsRepo, devicesRepo, checkService)
        runCurrent()

        assertEquals(1, devicesRepo.observedGroupIds.size)
        assertEquals("Group 1", viewModel.state.value.rows.first().groupName)

        val g1Renamed = Group("g1", "Group 1 Renamed", "u1", 1000L)
        groupsRepo.membershipsFlow.value = listOf(GroupMembership(g1Renamed, GroupRole.OWNER))
        runCurrent()

        assertEquals(1, devicesRepo.observedGroupIds.size)
        assertEquals("Group 1 Renamed", viewModel.state.value.rows.first().groupName)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun removeFailureShowsError() = runTest {
        val checkService = FakeDeviceCheckService()
        val auth = FakeMyDevicesSessionProvider(User("u1", "u1@x.com", "User 1", emailVerified = true))
        val groupsRepo = FakeGroupRepository()
        val devicesRepo = FakeGroupDeviceRepository().apply {
            removeResult = Result.failure(IllegalStateException("Failed to delete device"))
        }

        val g1 = Group("g1", "Group 1", "u1", 1000L)
        groupsRepo.membershipsFlow.value = listOf(GroupMembership(g1, GroupRole.OWNER))
        val dev1 = HostDevice("d1", "Dev 1", "Model 1", groupId = "g1")
        devicesRepo.devicesFlow.value = listOf(dev1)

        val viewModel = MyDevicesViewModel(auth, groupsRepo, devicesRepo, checkService)
        runCurrent()

        viewModel.remove("d1")
        runCurrent()

        assertEquals("Failed to delete device", viewModel.state.value.error)
        viewModel.viewModelScope.cancel()
    }
}
