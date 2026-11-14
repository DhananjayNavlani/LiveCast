package com.dhananjay.livecast.client.group

import androidx.lifecycle.ViewModelStore
import com.dhananjay.livecast.protocol.AppEvent
import com.dhananjay.livecast.protocol.AppEventType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
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

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeAppEventRepository : AppEventRepository {
        var observedDeviceId: String? = null
        var observedGroupId: String? = null
        var observedLimit: Int? = null
        var flowToReturn: Flow<List<AppEvent>> = MutableSharedFlow()

        override fun observeRecent(deviceId: String, groupId: String, limit: Int): Flow<List<AppEvent>> {
            observedDeviceId = deviceId
            observedGroupId = groupId
            observedLimit = limit
            return flowToReturn
        }
    }

    @Test
    fun showsEvents() = runTest(testDispatcher) {
        val repo = FakeAppEventRepository()
        val events = listOf(
            AppEvent(
                id = "e1",
                type = AppEventType.INSTALLED,
                packageName = "com.whatsapp",
                label = "WhatsApp",
                atEpochMs = 1000L,
                detectedLate = false,
                groupId = "g1",
            ),
            AppEvent(
                id = "e2",
                type = AppEventType.REMOVED,
                packageName = "com.instagram",
                label = "Instagram",
                atEpochMs = 2000L,
                detectedLate = true,
                groupId = "g1",
            )
        )
        val flow = MutableSharedFlow<List<AppEvent>>()
        repo.flowToReturn = flow

        val viewModel = DeviceDetailViewModel(deviceId = "d1", groupId = "g1", events = repo)
        runCurrent()

        assertTrue(viewModel.state.value.loading)
        assertNull(viewModel.state.value.error)
        assertTrue(viewModel.state.value.events.isEmpty())

        flow.emit(events)
        runCurrent()

        assertFalse(viewModel.state.value.loading)
        assertNull(viewModel.state.value.error)
        assertEquals(events, viewModel.state.value.events)
    }

    @Test
    fun emptyShowsNoEvents() = runTest(testDispatcher) {
        val repo = FakeAppEventRepository()
        val flow = MutableSharedFlow<List<AppEvent>>()
        repo.flowToReturn = flow

        val viewModel = DeviceDetailViewModel(deviceId = "d1", groupId = "g1", events = repo)
        runCurrent()

        flow.emit(emptyList())
        runCurrent()

        assertFalse(viewModel.state.value.loading)
        assertNull(viewModel.state.value.error)
        assertTrue(viewModel.state.value.events.isEmpty())
    }

    @Test
    fun accessLostShowsMessage() = runTest(testDispatcher) {
        val repo = FakeAppEventRepository()
        repo.flowToReturn = flow {
            throw GroupError.AccessLost
        }

        val viewModel = DeviceDetailViewModel(deviceId = "d1", groupId = "g1", events = repo)
        runCurrent()

        assertFalse(viewModel.state.value.loading)
        assertEquals("You no longer have access", viewModel.state.value.error)
        assertTrue(viewModel.state.value.events.isEmpty())
    }

    @Test
    fun otherErrorShowsMessage() = runTest(testDispatcher) {
        val repo = FakeAppEventRepository()
        repo.flowToReturn = flow {
            throw RuntimeException("Network dropped")
        }

        val viewModel = DeviceDetailViewModel(deviceId = "d1", groupId = "g1", events = repo)
        runCurrent()

        assertFalse(viewModel.state.value.loading)
        assertEquals("Network dropped", viewModel.state.value.error)
        assertTrue(viewModel.state.value.events.isEmpty())
    }

    @Test
    fun clearedViewModelStopsCollecting() = runTest(testDispatcher) {
        var cancelled = false
        val repo = object : AppEventRepository {
            override fun observeRecent(deviceId: String, groupId: String, limit: Int): Flow<List<AppEvent>> {
                return callbackFlow {
                    awaitClose {
                        cancelled = true
                    }
                }
            }
        }

        val store = ViewModelStore()
        val viewModel = DeviceDetailViewModel(deviceId = "d1", groupId = "g1", events = repo)
        store.put("test_vm", viewModel)
        runCurrent()

        assertFalse(cancelled)

        store.clear()
        runCurrent()

        assertTrue(cancelled, "Expected repository flow collection to be cancelled after ViewModelStore.clear()")
    }

    @Test
    fun messageContainingPermissionDeniedIsNotAccessLost() = runTest(testDispatcher) {
        val repo = FakeAppEventRepository()
        repo.flowToReturn = flow {
            throw RuntimeException("PERMISSION_DENIED from some other source")
        }

        val viewModel = DeviceDetailViewModel(deviceId = "d1", groupId = "g1", events = repo)
        runCurrent()

        assertFalse(viewModel.state.value.loading)
        assertEquals("PERMISSION_DENIED from some other source", viewModel.state.value.error)
    }

    @Test
    fun unsupportedRepositoryShowsMessage() = runTest(testDispatcher) {
        val repo = UnsupportedAppEventRepository("Custom unsupported message")
        val viewModel = DeviceDetailViewModel(deviceId = "d1", groupId = "g1", events = repo)
        runCurrent()

        assertFalse(viewModel.state.value.loading)
        assertEquals("Custom unsupported message", viewModel.state.value.error)
    }
}
