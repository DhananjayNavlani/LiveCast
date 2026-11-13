package com.dhananjay.livecast.pairing

import com.dhananjay.livecast.protocol.HostDevice
import com.dhananjay.livecast.protocol.PairingCode
import com.dhananjay.livecast.protocol.PairingRequest
import com.dhananjay.livecast.protocol.PairingStatus
import com.dhananjay.livecast.protocol.Supervisor
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HostPairingViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeDeviceIdentity(override var deviceId: String? = "H") : DeviceIdentity

    private class FakeHostDeviceInfoProvider : HostDeviceInfoProvider {
        override fun current(deviceId: String): HostDevice = HostDevice(
            id = deviceId,
            displayName = "Android",
            model = "Android"
        )
    }

    private class FakeDeviceRegistry : DeviceRegistry {
        var upsertDeviceResult: Result<Unit> = Result.success(Unit)
        val upsertedDevices = mutableListOf<HostDevice>()

        override suspend fun upsertDevice(device: HostDevice): Result<Unit> {
            upsertedDevices.add(device)
            return upsertDeviceResult
        }
    }

    private class FakePairingCodeIssuer : PairingCodeIssuer {
        var createCodeCalls = 0
        var nextCode: PairingCode? = null

        override suspend fun createCode(deviceId: String): Result<PairingCode> {
            createCodeCalls++
            val code = nextCode ?: PairingCode(
                code = "123456",
                deviceId = deviceId,
                hostUid = deviceId,
                createdAtEpochMs = 1000L,
                expiresAtEpochMs = 7000L,
            )
            return Result.success(code)
        }
    }

    private class FakePairingRequestInbox : PairingRequestInbox {
        val approvedRequests = mutableListOf<PairingRequest>()
        val deniedRequests = mutableListOf<PairingRequest>()
        val pendingRequestsFlow = MutableStateFlow<List<PairingRequest>>(emptyList())

        override fun observePendingRequests(deviceId: String): Flow<List<PairingRequest>> =
            pendingRequestsFlow

        override suspend fun approve(request: PairingRequest): Result<Unit> {
            approvedRequests.add(request)
            return Result.success(Unit)
        }

        override suspend fun deny(request: PairingRequest): Result<Unit> {
            deniedRequests.add(request)
            return Result.success(Unit)
        }
    }

    private class FakeSupervisorDirectory : SupervisorDirectory {
        val supervisorsFlow = MutableStateFlow<List<Supervisor>>(emptyList())

        override fun observeSupervisors(deviceId: String): Flow<List<Supervisor>> =
            supervisorsFlow
    }

    @Test
    fun visibleRequestsFiltersOldAndOtherCodes() {
        val codeA = PairingCode(
            code = "111111",
            deviceId = "H",
            hostUid = "H",
            createdAtEpochMs = 100L,
            expiresAtEpochMs = 700L,
        )

        val reqOld = PairingRequest(
            id = "r1",
            code = "111111",
            deviceId = "H",
            clientUid = "P1",
            clientName = "P1",
            clientEmail = null,
            status = PairingStatus.PENDING,
            createdAtEpochMs = 50L,
        )

        val reqMatching = PairingRequest(
            id = "r2",
            code = "111111",
            deviceId = "H",
            clientUid = "P2",
            clientName = "P2",
            clientEmail = "p2@test.com",
            status = PairingStatus.PENDING,
            createdAtEpochMs = 150L,
        )

        val reqOtherCode = PairingRequest(
            id = "r3",
            code = "222222",
            deviceId = "H",
            clientUid = "P3",
            clientName = "P3",
            clientEmail = null,
            status = PairingStatus.PENDING,
            createdAtEpochMs = 150L,
        )

        val allPending = listOf(reqOld, reqMatching, reqOtherCode)

        // At now = 200 (< 700): only reqMatching survives
        val visibleAt200 = visibleRequests(allPending, codeA, nowEpochMs = 200L)
        assertEquals(listOf(reqMatching), visibleAt200)

        // At now = 700 (>= expiresAt): none survive once expired
        val visibleAt700 = visibleRequests(allPending, codeA, nowEpochMs = 700L)
        assertTrue(visibleAt700.isEmpty())
    }

    @Test
    fun approveDelegatesToRepo() = runTest {
        val fakeIdentity = FakeDeviceIdentity("H")
        val fakeDeviceInfo = FakeHostDeviceInfoProvider()
        val fakeRegistry = FakeDeviceRegistry()
        val fakeCodes = FakePairingCodeIssuer()
        val fakeInbox = FakePairingRequestInbox()
        val fakeSupervisors = FakeSupervisorDirectory()

        val viewModel = HostPairingViewModel(
            identity = fakeIdentity,
            deviceInfo = fakeDeviceInfo,
            registry = fakeRegistry,
            codes = fakeCodes,
            inbox = fakeInbox,
            supervisors = fakeSupervisors,
            clock = { 2000L }
        )

        val req = PairingRequest(
            id = "r1",
            code = "123456",
            deviceId = "H",
            clientUid = "P",
            clientName = "Parent",
            clientEmail = "parent@test.com",
            status = PairingStatus.PENDING,
            createdAtEpochMs = 1500L,
        )

        viewModel.approve(req)
        advanceUntilIdle()

        assertEquals(listOf(req), fakeInbox.approvedRequests)
    }

    @Test
    fun denyDelegatesToRepo() = runTest {
        val fakeIdentity = FakeDeviceIdentity("H")
        val fakeDeviceInfo = FakeHostDeviceInfoProvider()
        val fakeRegistry = FakeDeviceRegistry()
        val fakeCodes = FakePairingCodeIssuer()
        val fakeInbox = FakePairingRequestInbox()
        val fakeSupervisors = FakeSupervisorDirectory()

        val viewModel = HostPairingViewModel(
            identity = fakeIdentity,
            deviceInfo = fakeDeviceInfo,
            registry = fakeRegistry,
            codes = fakeCodes,
            inbox = fakeInbox,
            supervisors = fakeSupervisors,
            clock = { 2000L }
        )

        val req = PairingRequest(
            id = "r1",
            code = "123456",
            deviceId = "H",
            clientUid = "P",
            clientName = "Parent",
            clientEmail = "parent@test.com",
            status = PairingStatus.PENDING,
            createdAtEpochMs = 1500L,
        )

        viewModel.deny(req)
        advanceUntilIdle()

        assertEquals(listOf(req), fakeInbox.deniedRequests)
    }

    @Test
    fun generateCodeExposesCountdown() = runTest {
        val fakeIdentity = FakeDeviceIdentity("H")
        val fakeDeviceInfo = FakeHostDeviceInfoProvider()
        val fakeRegistry = FakeDeviceRegistry()
        val fakeCodes = FakePairingCodeIssuer()
        val fakeInbox = FakePairingRequestInbox()
        val fakeSupervisors = FakeSupervisorDirectory()

        val code = PairingCode(
            code = "123456",
            deviceId = "H",
            hostUid = "H",
            createdAtEpochMs = 1000L,
            expiresAtEpochMs = 7000L,
        )
        fakeCodes.nextCode = code

        val viewModel = HostPairingViewModel(
            identity = fakeIdentity,
            deviceInfo = fakeDeviceInfo,
            registry = fakeRegistry,
            codes = fakeCodes,
            inbox = fakeInbox,
            supervisors = fakeSupervisors,
            clock = { 2000L }
        )

        viewModel.generateCode()
        advanceUntilIdle()

        assertEquals<PairingCode?>(code, viewModel.uiState.value.code)
        // remainingMs = expiresAt - clock() = 7000L - 2000L = 5000L
        assertEquals(5000L, viewModel.uiState.value.remainingMs)
        assertEquals(listOf(fakeDeviceInfo.current("H")), fakeRegistry.upsertedDevices)
    }

    @Test
    fun approveConsumesCode() = runTest {
        val fakeIdentity = FakeDeviceIdentity("H")
        val fakeDeviceInfo = FakeHostDeviceInfoProvider()
        val fakeRegistry = FakeDeviceRegistry()
        val fakeCodes = FakePairingCodeIssuer()
        val fakeInbox = FakePairingRequestInbox()
        val fakeSupervisors = FakeSupervisorDirectory()

        val code = PairingCode(
            code = "123456",
            deviceId = "H",
            hostUid = "H",
            createdAtEpochMs = 1000L,
            expiresAtEpochMs = 7000L,
        )
        fakeCodes.nextCode = code

        val viewModel = HostPairingViewModel(
            identity = fakeIdentity,
            deviceInfo = fakeDeviceInfo,
            registry = fakeRegistry,
            codes = fakeCodes,
            inbox = fakeInbox,
            supervisors = fakeSupervisors,
            clock = { 2000L }
        )

        viewModel.generateCode()
        advanceUntilIdle()
        assertEquals<PairingCode?>(code, viewModel.uiState.value.code)

        val req = PairingRequest(
            id = "r1",
            code = "123456",
            deviceId = "H",
            clientUid = "P",
            clientName = "Parent",
            clientEmail = "parent@test.com",
            status = PairingStatus.PENDING,
            createdAtEpochMs = 1500L,
        )

        viewModel.approve(req)
        advanceUntilIdle()

        assertEquals(listOf(req), fakeInbox.approvedRequests)
        assertEquals<PairingCode?>(null, viewModel.uiState.value.code)
        assertTrue(viewModel.uiState.value.requests.isEmpty())
        assertEquals(0L, viewModel.uiState.value.remainingMs)
    }

    @Test
    fun generateCodeStopsWhenDeviceUpsertFails() = runTest {
        val fakeIdentity = FakeDeviceIdentity("H")
        val fakeDeviceInfo = FakeHostDeviceInfoProvider()
        val fakeRegistry = FakeDeviceRegistry()
        fakeRegistry.upsertDeviceResult = Result.failure(Exception("Upsert failed"))
        val fakeCodes = FakePairingCodeIssuer()
        val fakeInbox = FakePairingRequestInbox()
        val fakeSupervisors = FakeSupervisorDirectory()

        val viewModel = HostPairingViewModel(
            identity = fakeIdentity,
            deviceInfo = fakeDeviceInfo,
            registry = fakeRegistry,
            codes = fakeCodes,
            inbox = fakeInbox,
            supervisors = fakeSupervisors,
            clock = { 2000L }
        )

        viewModel.generateCode()
        advanceUntilIdle()

        assertEquals(0, fakeCodes.createCodeCalls)
        assertEquals("Upsert failed", viewModel.uiState.value.error)
        assertEquals(false, viewModel.uiState.value.busy)
        assertEquals<PairingCode?>(null, viewModel.uiState.value.code)
    }

    @Test
    fun createCodeCollisionDetection() {
        val collisionEx = FirebaseFirestoreException("doc exists", FirebaseFirestoreException.Code.ALREADY_EXISTS)
        val wrappedCollision = RuntimeException("transaction failed", collisionEx)
        val permissionDenied = FirebaseFirestoreException("permission denied", FirebaseFirestoreException.Code.PERMISSION_DENIED)
        val otherEx = RuntimeException("network error")

        assertTrue(isAlreadyExistsException(collisionEx))
        assertTrue(isAlreadyExistsException(wrappedCollision))
        assertFalse(isAlreadyExistsException(permissionDenied))
        assertFalse(isAlreadyExistsException(otherEx))
    }

    @Test
    fun pendingRequestsStreamErrorSetsError() = runTest {
        val fakeIdentity = FakeDeviceIdentity("H")
        val fakeDeviceInfo = FakeHostDeviceInfoProvider()
        val fakeRegistry = FakeDeviceRegistry()
        val fakeCodes = FakePairingCodeIssuer()
        val fakeInbox = object : PairingRequestInbox {
            override fun observePendingRequests(deviceId: String): Flow<List<PairingRequest>> = flow {
                throw RuntimeException("Inbox stream error")
            }
            override suspend fun approve(request: PairingRequest): Result<Unit> = Result.success(Unit)
            override suspend fun deny(request: PairingRequest): Result<Unit> = Result.success(Unit)
        }
        val fakeSupervisors = FakeSupervisorDirectory()

        val viewModel = HostPairingViewModel(
            identity = fakeIdentity,
            deviceInfo = fakeDeviceInfo,
            registry = fakeRegistry,
            codes = fakeCodes,
            inbox = fakeInbox,
            supervisors = fakeSupervisors,
            clock = { 2000L }
        )
        advanceUntilIdle()

        assertEquals("Inbox stream error", viewModel.uiState.value.error)
    }

    @Test
    fun supervisorsStreamErrorSetsError() = runTest {
        val fakeIdentity = FakeDeviceIdentity("H")
        val fakeDeviceInfo = FakeHostDeviceInfoProvider()
        val fakeRegistry = FakeDeviceRegistry()
        val fakeCodes = FakePairingCodeIssuer()
        val fakeInbox = FakePairingRequestInbox()
        val fakeSupervisors = object : SupervisorDirectory {
            override fun observeSupervisors(deviceId: String): Flow<List<Supervisor>> = flow {
                throw RuntimeException("Supervisors stream error")
            }
        }

        val viewModel = HostPairingViewModel(
            identity = fakeIdentity,
            deviceInfo = fakeDeviceInfo,
            registry = fakeRegistry,
            codes = fakeCodes,
            inbox = fakeInbox,
            supervisors = fakeSupervisors,
            clock = { 2000L }
        )
        advanceUntilIdle()

        assertEquals("Supervisors stream error", viewModel.uiState.value.error)
    }
}
