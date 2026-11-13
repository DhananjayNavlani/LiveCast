package com.dhananjay.livecast.pairing

import com.dhananjay.livecast.protocol.Supervisor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    private class FakeSupervisorDirectory : SupervisorDirectory {
        val supervisorsFlow = MutableStateFlow<List<Supervisor>>(emptyList())
        var observeCalls = 0

        override fun observeSupervisors(deviceId: String): Flow<List<Supervisor>> {
            observeCalls++
            return supervisorsFlow
        }
    }

    private class FakeDeviceIdentity(override val deviceId: String?) : DeviceIdentity

    @Test
    fun listsSupervisorNames() = runTest {
        val fakeIdentity = FakeDeviceIdentity("dev-123")
        val fakeDirectory = FakeSupervisorDirectory()
        val viewModel = SupervisedByViewModel(fakeIdentity, fakeDirectory)

        val supervisors = listOf(
            Supervisor(
                uid = "u1",
                name = "Alice",
                email = "alice@example.com",
                requestId = "r1",
                addedAtEpochMs = 1000L
            ),
            Supervisor(
                uid = "u2",
                name = "Bob",
                email = "bob@example.com",
                requestId = "r2",
                addedAtEpochMs = 2000L
            )
        )
        fakeDirectory.supervisorsFlow.value = supervisors
        advanceUntilIdle()

        assertEquals(listOf("Alice", "Bob"), viewModel.supervisorNames.value)
        assertEquals(supervisors, viewModel.supervisors.value)
        assertEquals(1, fakeDirectory.observeCalls)
    }

    @Test
    fun emptyWhenNoDeviceId() = runTest {
        val fakeIdentity = FakeDeviceIdentity(null)
        val fakeDirectory = FakeSupervisorDirectory()
        val viewModel = SupervisedByViewModel(fakeIdentity, fakeDirectory)

        fakeDirectory.supervisorsFlow.value = listOf(
            Supervisor(
                uid = "u1",
                name = "Alice",
                email = "alice@example.com",
                requestId = "r1",
                addedAtEpochMs = 1000L
            )
        )
        advanceUntilIdle()

        assertTrue(viewModel.supervisors.value.isEmpty())
        assertTrue(viewModel.supervisorNames.value.isEmpty())
        assertEquals(0, fakeDirectory.observeCalls)
    }
}
