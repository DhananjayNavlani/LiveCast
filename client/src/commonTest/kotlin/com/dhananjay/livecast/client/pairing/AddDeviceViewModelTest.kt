package com.dhananjay.livecast.client.pairing

import com.dhananjay.livecast.auth.AuthState
import com.dhananjay.livecast.auth.SessionProvider
import com.dhananjay.livecast.auth.User
import com.dhananjay.livecast.protocol.PairingRequest
import com.dhananjay.livecast.protocol.PairingStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
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
import kotlin.test.assertIs

class FakeSessionProvider(
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

class FakePairingRequestRepository : PairingRequestRepository {
    var submitResult: Result<SubmittedRequest>? = null
    var submitCount = 0
    var submitDelayMs = 0L
    var lastSubmittedCode: String? = null
    val requestFlows = mutableMapOf<String, MutableStateFlow<PairingRequest?>>()
    var requestFlowProvider: ((String) -> Flow<PairingRequest?>)? = null

    override suspend fun submitCode(code: String): Result<SubmittedRequest> {
        submitCount++
        lastSubmittedCode = code
        if (submitDelayMs > 0) {
            delay(submitDelayMs)
        }
        return submitResult ?: Result.failure(PairingError.InvalidOrExpiredCode)
    }

    override fun observeRequest(requestId: String): Flow<PairingRequest?> {
        return requestFlowProvider?.invoke(requestId) ?: requestFlows.getOrPut(requestId) {
            val req = submitResult?.getOrNull()?.request
            MutableStateFlow(if (req?.id == requestId) req else null)
        }
    }

    fun emitRequest(requestId: String, request: PairingRequest?) {
        requestFlows.getOrPut(requestId) { MutableStateFlow(null) }.value = request
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AddDeviceViewModelTest {

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
    fun codeInputDigitsOnlyMax6() {
        val auth = FakeSessionProvider(User("u1", "u1@example.com", "User 1"))
        val repo = FakePairingRequestRepository()
        val viewModel = AddDeviceViewModel(repo, auth)

        viewModel.onCodeChanged("12a3456789")
        val state = viewModel.addState.value
        assertIs<AddDeviceState.Entering>(state)
        assertEquals("123456", state.code)
    }

    @Test
    fun anonymousCannotSubmit() = runTest {
        val auth = FakeSessionProvider(User("u_anon", null, null, isAnonymous = true))
        val repo = FakePairingRequestRepository()
        val viewModel = AddDeviceViewModel(repo, auth)

        viewModel.onCodeChanged("123456")
        viewModel.submit()
        testScheduler.runCurrent()

        val state = viewModel.addState.value
        assertIs<AddDeviceState.Entering>(state)
        assertEquals("Sign in with an account to add a device", state.error)
        assertEquals(0, repo.submitCount)
    }

    @Test
    fun invalidCodeShowsError() = runTest {
        val auth = FakeSessionProvider(User("u1", "u1@example.com", "User 1"))
        val repo = FakePairingRequestRepository().apply {
            submitResult = Result.failure(PairingError.InvalidOrExpiredCode)
        }
        val viewModel = AddDeviceViewModel(repo, auth)

        viewModel.onCodeChanged("123456")
        viewModel.submit()
        testScheduler.runCurrent()

        val state = viewModel.addState.value
        assertIs<AddDeviceState.Entering>(state)
        assertEquals("Code invalid or expired", state.error)
    }

    @Test
    fun approvedFlow() = runTest {
        val now = 1000L
        val auth = FakeSessionProvider(User("u1", "u1@example.com", "User 1"))
        val pendingRequest = PairingRequest(
            id = "req1",
            code = "123456",
            deviceId = "d1",
            clientUid = "u1",
            clientName = "User 1",
            clientEmail = "u1@example.com",
            status = PairingStatus.PENDING,
            createdAtEpochMs = now,
            decidedAtEpochMs = null
        )
        val repo = FakePairingRequestRepository().apply {
            submitResult = Result.success(SubmittedRequest(pendingRequest, codeExpiresAtEpochMs = now + 600_000L))
        }
        val viewModel = AddDeviceViewModel(repo, auth, clock = { now })

        viewModel.onCodeChanged("123456")
        viewModel.submit()

        assertIs<AddDeviceState.Submitting>(viewModel.addState.value)
        testScheduler.runCurrent()

        val waitingState = viewModel.addState.value
        assertIs<AddDeviceState.Waiting>(waitingState)
        assertEquals("req1", waitingState.request.id)

        repo.emitRequest("req1", pendingRequest.copy(status = PairingStatus.APPROVED))
        testScheduler.runCurrent()

        val approvedState = viewModel.addState.value
        assertIs<AddDeviceState.Approved>(approvedState)
        assertEquals("d1", approvedState.deviceId)
    }

    @Test
    fun deniedFlow() = runTest {
        val now = 1000L
        val auth = FakeSessionProvider(User("u1", "u1@example.com", "User 1"))
        val pendingRequest = PairingRequest(
            id = "req1",
            code = "123456",
            deviceId = "d1",
            clientUid = "u1",
            clientName = "User 1",
            clientEmail = "u1@example.com",
            status = PairingStatus.PENDING,
            createdAtEpochMs = now,
            decidedAtEpochMs = null
        )
        val repo = FakePairingRequestRepository().apply {
            submitResult = Result.success(SubmittedRequest(pendingRequest, codeExpiresAtEpochMs = now + 600_000L))
        }
        val viewModel = AddDeviceViewModel(repo, auth, clock = { now })

        viewModel.onCodeChanged("123456")
        viewModel.submit()
        testScheduler.runCurrent()

        assertIs<AddDeviceState.Waiting>(viewModel.addState.value)

        repo.emitRequest("req1", pendingRequest.copy(status = PairingStatus.DENIED))
        testScheduler.runCurrent()

        assertIs<AddDeviceState.Denied>(viewModel.addState.value)
    }

    @Test
    fun expiresWhileWaiting() = runTest {
        val auth = FakeSessionProvider(User("u1", "u1@example.com", "User 1"))
        val expiresAt = 601_000L
        val pendingRequest = PairingRequest(
            id = "req1",
            code = "123456",
            deviceId = "d1",
            clientUid = "u1",
            clientName = "User 1",
            clientEmail = "u1@example.com",
            status = PairingStatus.PENDING,
            createdAtEpochMs = 1000L,
            decidedAtEpochMs = null
        )
        val repo = FakePairingRequestRepository().apply {
            submitResult = Result.success(SubmittedRequest(pendingRequest, codeExpiresAtEpochMs = expiresAt))
        }
        val viewModel = AddDeviceViewModel(repo, auth, clock = { testScheduler.currentTime })

        viewModel.onCodeChanged("123456")
        viewModel.submit()
        testScheduler.runCurrent()

        assertIs<AddDeviceState.Waiting>(viewModel.addState.value)

        testScheduler.advanceTimeBy(expiresAt + 1L)

        assertIs<AddDeviceState.Expired>(viewModel.addState.value)
    }

    @Test
    fun submitIgnoredWhileBusy() = runTest {
        val now = 1000L
        val auth = FakeSessionProvider(User("u1", "u1@example.com", "User 1"))
        val pendingRequest = PairingRequest(
            id = "req1",
            code = "123456",
            deviceId = "d1",
            clientUid = "u1",
            clientName = "User 1",
            clientEmail = "u1@example.com",
            status = PairingStatus.PENDING,
            createdAtEpochMs = now,
            decidedAtEpochMs = null
        )
        val repo = FakePairingRequestRepository().apply {
            submitDelayMs = 5000L
            submitResult = Result.success(SubmittedRequest(pendingRequest, codeExpiresAtEpochMs = now + 600_000L))
        }
        val viewModel = AddDeviceViewModel(repo, auth, clock = { now })

        viewModel.onCodeChanged("123456")
        viewModel.submit()
        // double-tap submit while busy
        viewModel.submit()

        testScheduler.advanceTimeBy(5001L)
        testScheduler.runCurrent()

        assertEquals(1, repo.submitCount)
        assertIs<AddDeviceState.Waiting>(viewModel.addState.value)
    }

    @Test
    fun requestStreamErrorReturnsToEntering() = runTest {
        val now = 1000L
        val auth = FakeSessionProvider(User("u1", "u1@example.com", "User 1"))
        val pendingRequest = PairingRequest(
            id = "req1",
            code = "123456",
            deviceId = "d1",
            clientUid = "u1",
            clientName = "User 1",
            clientEmail = "u1@example.com",
            status = PairingStatus.PENDING,
            createdAtEpochMs = now,
            decidedAtEpochMs = null
        )
        val repo = FakePairingRequestRepository().apply {
            submitResult = Result.success(SubmittedRequest(pendingRequest, codeExpiresAtEpochMs = now + 600_000L))
            requestFlowProvider = { flow { throw RuntimeException("Permission denied on request stream") } }
        }
        val viewModel = AddDeviceViewModel(repo, auth, clock = { now })

        viewModel.onCodeChanged("123456")
        viewModel.submit()
        testScheduler.runCurrent()

        val state = viewModel.addState.value
        assertIs<AddDeviceState.Entering>(state)
        assertEquals("123456", state.code)
        assertEquals("Permission denied on request stream", state.error)
    }
}
