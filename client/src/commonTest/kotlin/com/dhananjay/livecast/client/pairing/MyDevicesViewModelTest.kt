package com.dhananjay.livecast.client.pairing

import com.dhananjay.livecast.auth.SessionProvider
import com.dhananjay.livecast.auth.User
import com.dhananjay.livecast.protocol.HostDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class FakeSupervisedDeviceRepository : SupervisedDeviceRepository {
    var myDevicesFlow: Flow<List<HostDevice>> = MutableStateFlow<List<HostDevice>>(emptyList())
    var devicesFlowProvider: (() -> Flow<List<HostDevice>>)? = null
    var removeResult: Result<Unit> = Result.success(Unit)
    val removedDeviceIds = mutableListOf<String>()

    override fun observeMyDevices(): Flow<List<HostDevice>> =
        devicesFlowProvider?.invoke() ?: myDevicesFlow

    override suspend fun removeDevice(deviceId: String): Result<Unit> {
        removedDeviceIds.add(deviceId)
        return removeResult
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
    fun removeDelegates() = runTest {
        val auth = FakeSessionProvider(User("u1", "u1@example.com", "User 1"))
        val repo = FakeSupervisedDeviceRepository()
        val viewModel = MyDevicesViewModel(repo, auth)

        viewModel.remove("d1")
        testScheduler.runCurrent()

        assertEquals(listOf("d1"), repo.removedDeviceIds)
    }

    @Test
    fun devicesStreamErrorSetsErrorNoCrash() = runTest {
        val auth = FakeSessionProvider(User("u1", "u1@example.com", "User 1"))
        val repo = FakeSupervisedDeviceRepository().apply {
            myDevicesFlow = flow { throw RuntimeException("Network error on devices stream") }
        }
        val viewModel = MyDevicesViewModel(repo, auth)
        testScheduler.runCurrent()

        assertEquals("Network error on devices stream", viewModel.devicesError.value)
        assertEquals(emptyList(), viewModel.devices.value)
    }

    @Test
    fun devicesResubscribeOnUserChange() = runTest {
        val userA = User("u1", "u1@example.com", "User 1")
        val userB = User("u2", "u2@example.com", "User 2")
        val auth = FakeSessionProvider(userA)

        val deviceA = HostDevice("d1", "Phone A", "Pixel")
        val deviceB = HostDevice("d2", "Phone B", "Samsung")

        val repo = FakeSupervisedDeviceRepository().apply {
            devicesFlowProvider = {
                val currentUid = auth.currentUser?.id
                if (currentUid == "u1") flowOf(listOf(deviceA))
                else if (currentUid == "u2") flowOf(listOf(deviceB))
                else flowOf(emptyList())
            }
        }
        val viewModel = MyDevicesViewModel(repo, auth)
        testScheduler.runCurrent()

        assertEquals(listOf(deviceA), viewModel.devices.value)

        auth.setUser(userB)
        testScheduler.runCurrent()

        assertEquals(listOf(deviceB), viewModel.devices.value)
    }

    @Test
    fun removeFailureSetsError() = runTest {
        val auth = FakeSessionProvider(User("u1", "u1@example.com", "User 1"))
        val repo = FakeSupervisedDeviceRepository().apply {
            removeResult = Result.failure(RuntimeException("Delete denied"))
        }
        val viewModel = MyDevicesViewModel(repo, auth)
        testScheduler.runCurrent()

        viewModel.remove("d1")
        testScheduler.runCurrent()

        assertEquals("Delete denied", viewModel.devicesError.value)
    }
}
