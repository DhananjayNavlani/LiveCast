package com.dhananjay.livecast.pairing

import com.dhananjay.livecast.protocol.HostDevice
import com.dhananjay.livecast.protocol.PairingCode
import com.dhananjay.livecast.protocol.PairingRequest
import com.dhananjay.livecast.protocol.PairingStatus
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

    private class FakeHostPairingRepository : HostPairingRepository {
        val approvedRequests = mutableListOf<PairingRequest>()
        val deniedRequests = mutableListOf<PairingRequest>()
        var nextCode: PairingCode? = null
        val pendingRequestsFlow = MutableStateFlow<List<PairingRequest>>(emptyList())
        val supervisorsFlow = MutableStateFlow<List<Supervisor>>(emptyList())

        override suspend fun upsertDevice(device: HostDevice): Result<Unit> = Result.success(Unit)

        override suspend fun createCode(deviceId: String): Result<PairingCode> {
            val code = nextCode ?: PairingCode(
                code = "123456",
                deviceId = deviceId,
                hostUid = deviceId,
                createdAtEpochMs = 1000L,
                expiresAtEpochMs = 7000L,
            )
            return Result.success(code)
        }

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
        val fakeRepo = FakeHostPairingRepository()
        val viewModel = HostPairingViewModel(
            repo = fakeRepo,
            auth = null,
            clock = { 2000L },
            deviceIdProvider = { "H" },
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

        assertEquals(listOf(req), fakeRepo.approvedRequests)
    }

    @Test
    fun denyDelegatesToRepo() = runTest {
        val fakeRepo = FakeHostPairingRepository()
        val viewModel = HostPairingViewModel(
            repo = fakeRepo,
            auth = null,
            clock = { 2000L },
            deviceIdProvider = { "H" },
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

        assertEquals(listOf(req), fakeRepo.deniedRequests)
    }

    @Test
    fun generateCodeExposesCountdown() = runTest {
        val fakeRepo = FakeHostPairingRepository()
        val code = PairingCode(
            code = "123456",
            deviceId = "H",
            hostUid = "H",
            createdAtEpochMs = 1000L,
            expiresAtEpochMs = 7000L,
        )
        fakeRepo.nextCode = code

        val viewModel = HostPairingViewModel(
            repo = fakeRepo,
            auth = null,
            clock = { 2000L },
            deviceIdProvider = { "H" },
        )

        viewModel.generateCode()
        advanceUntilIdle()

        assertEquals<PairingCode?>(code, viewModel.uiState.value.code)
        // remainingMs = expiresAt - clock() = 7000L - 2000L = 5000L
        assertEquals(5000L, viewModel.uiState.value.remainingMs)
    }
}
