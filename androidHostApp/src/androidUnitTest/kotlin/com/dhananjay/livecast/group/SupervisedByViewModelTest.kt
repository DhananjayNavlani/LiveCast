package com.dhananjay.livecast.group

import com.dhananjay.livecast.protocol.GroupMember
import com.dhananjay.livecast.protocol.GroupRole
import com.dhananjay.livecast.protocol.HostDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SupervisedByViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeDeviceRegistry : DeviceRegistry {
        val deviceFlow = MutableSharedFlow<HostDevice?>(replay = 1)
        var observedDeviceId: String? = null

        override fun observeDevice(deviceId: String): Flow<HostDevice?> {
            observedDeviceId = deviceId
            return deviceFlow
        }

        override suspend fun enroll(device: HostDevice, groupId: String): Result<Unit> = Result.success(Unit)
    }

    private class FakeGroupMembersDirectory : GroupMembersDirectory {
        val observedGroups = mutableListOf<String>()
        val supervisorsFlows = mutableMapOf<String, MutableSharedFlow<List<GroupMember>>>()
        var throwForGroup: String? = null

        fun flowFor(groupId: String): MutableSharedFlow<List<GroupMember>> {
            return supervisorsFlows.getOrPut(groupId) { MutableSharedFlow(replay = 1) }
        }

        override fun observeSupervisors(groupId: String): Flow<List<GroupMember>> {
            observedGroups.add(groupId)
            if (throwForGroup == groupId) {
                return flow { throw RuntimeException("Listener error") }
            }
            return flowFor(groupId)
        }
    }

    private class FakeDeviceIdentity(override val deviceId: String?) : DeviceIdentity

    @Test
    fun namesFromGroupSupervisors() = runTest {
        val identity = FakeDeviceIdentity("dev-1")
        val registry = FakeDeviceRegistry()
        val members = FakeGroupMembersDirectory()

        registry.deviceFlow.tryEmit(HostDevice(id = "dev-1", displayName = "Pixel", model = "P8", groupId = "g1"))
        val supervisorList = listOf(
            GroupMember(groupId = "g1", email = "mum@x.com", role = GroupRole.OWNER, name = "Mum", addedAtEpochMs = 100L),
            GroupMember(groupId = "g1", email = "dad@x.com", role = GroupRole.SUPERVISOR, name = "Dad", addedAtEpochMs = 200L)
        )
        members.flowFor("g1").tryEmit(supervisorList)

        val vm = SupervisedByViewModel(identity, registry, members)
        advanceUntilIdle()

        assertEquals(true, vm.state.value.enrolled)
        assertEquals(listOf("Mum", "Dad"), vm.state.value.supervisorNames)
        assertEquals(null, vm.state.value.error)
    }

    @Test
    fun notEnrolledReportsFalse() = runTest {
        val identity = FakeDeviceIdentity("dev-1")
        val registry = FakeDeviceRegistry()
        val members = FakeGroupMembersDirectory()

        registry.deviceFlow.tryEmit(HostDevice(id = "dev-1", displayName = "Pixel", model = "P8", groupId = null))

        val vm = SupervisedByViewModel(identity, registry, members)
        advanceUntilIdle()

        assertEquals(false, vm.state.value.enrolled)
        assertTrue(vm.state.value.supervisorNames.isEmpty())
        assertTrue(members.observedGroups.isEmpty())
    }

    @Test
    fun groupChangeSwitchesListener() = runTest {
        val identity = FakeDeviceIdentity("dev-1")
        val registry = FakeDeviceRegistry()
        val members = FakeGroupMembersDirectory()

        registry.deviceFlow.tryEmit(HostDevice(id = "dev-1", displayName = "Pixel", model = "P8", groupId = "g1"))
        members.flowFor("g1").tryEmit(
            listOf(GroupMember(groupId = "g1", email = "mum@x.com", role = GroupRole.OWNER, name = "Mum", addedAtEpochMs = 100L))
        )

        val vm = SupervisedByViewModel(identity, registry, members)
        advanceUntilIdle()

        assertEquals(listOf("Mum"), vm.state.value.supervisorNames)

        // Switch group to g2
        members.flowFor("g2").tryEmit(
            listOf(GroupMember(groupId = "g2", email = "dad@x.com", role = GroupRole.OWNER, name = "Dad", addedAtEpochMs = 100L))
        )
        registry.deviceFlow.tryEmit(HostDevice(id = "dev-1", displayName = "Pixel", model = "P8", groupId = "g2"))
        advanceUntilIdle()

        assertEquals(listOf("Dad"), vm.state.value.supervisorNames)
        assertEquals(listOf("g1", "g2"), members.observedGroups)
    }

    @Test
    fun listenerErrorShowsError() = runTest {
        val identity = FakeDeviceIdentity("dev-1")
        val registry = FakeDeviceRegistry()
        val members = FakeGroupMembersDirectory()
        members.throwForGroup = "g1"

        registry.deviceFlow.tryEmit(HostDevice(id = "dev-1", displayName = "Pixel", model = "P8", groupId = "g1"))

        val vm = SupervisedByViewModel(identity, registry, members)
        advanceUntilIdle()

        assertEquals(true, vm.state.value.enrolled)
        assertNotNull(vm.state.value.error)
        assertTrue(vm.state.value.supervisorNames.isEmpty())
    }
}
