package com.dhananjay.livecast.client.pairing

import com.dhananjay.livecast.auth.AuthResult
import com.dhananjay.livecast.auth.AuthState
import com.dhananjay.livecast.auth.AuthService
import com.dhananjay.livecast.auth.User
import com.dhananjay.livecast.protocol.HostDevice
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
import kotlinx.coroutines.flow.flowOf
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

class FakeAuthService(
    override var currentUser: User? = null
) : AuthService {
    private val _authState = MutableStateFlow<AuthState>(
        currentUser?.let { AuthState.Authenticated(it) } ?: AuthState.Unauthenticated
    )
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    fun setUser(user: User?) {
        currentUser = user
        _authState.value = user?.let { AuthState.Authenticated(it) } ?: AuthState.Unauthenticated
    }

    override fun isLoggedIn(): Boolean = currentUser != null

    override suspend fun signInWithEmailAndPassword(email: String, password: String): AuthResult =
        currentUser?.let { AuthResult.Success(it) } ?: AuthResult.Error("Failed")

    override suspend fun createUserWithEmailAndPassword(email: String, password: String): AuthResult =
        currentUser?.let { AuthResult.Success(it) } ?: AuthResult.Error("Failed")

    override suspend fun signInAnonymously(): AuthResult =
        currentUser?.let { AuthResult.Success(it) } ?: AuthResult.Error("Failed")

    override suspend fun signOut() {
        currentUser = null
        _authState.value = AuthState.Unauthenticated
    }

    override suspend fun sendPasswordResetEmail(email: String): AuthResult =
        currentUser?.let { AuthResult.Success(it) } ?: AuthResult.Error("Failed")

    override suspend fun getIdToken(forceRefresh: Boolean): String? = "fake-token"
}

class FakeClientPairingRepository : ClientPairingRepository {
    var submitResult: Result<SubmittedRequest>? = null
    var submitCount = 0
    var submitDelayMs = 0L
    var lastSubmittedCode: String? = null
    val requestFlows = mutableMapOf<String, MutableStateFlow<PairingRequest?>>()
    var myDevicesFlow: Flow<List<HostDevice>> = MutableStateFlow<List<HostDevice>>(emptyList())
    var devicesFlowProvider: (() -> Flow<List<HostDevice>>)? = null
    var requestFlowProvider: ((String) -> Flow<PairingRequest?>)? = null
    var removeResult: Result<Unit> = Result.success(Unit)
    val removedDeviceIds = mutableListOf<String>()

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

    override fun observeMyDevices(): Flow<List<HostDevice>> =
        devicesFlowProvider?.invoke() ?: myDevicesFlow

    override suspend fun removeDevice(deviceId: String): Result<Unit> {
        removedDeviceIds.add(deviceId)
        return removeResult
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ClientPairingViewModelTest {

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
        val auth = FakeAuthService(User("u1", "u1@example.com", "User 1"))
        val repo = FakeClientPairingRepository()
        val viewModel = ClientPairingViewModel(repo, auth)

        viewModel.onCodeChanged("12a3456789")
        val state = viewModel.addState.value
        assertIs<AddDeviceState.Entering>(state)
        assertEquals("123456", state.code)
    }

    @Test
    fun anonymousCannotSubmit() = runTest {
        val auth = FakeAuthService(User("u_anon", null, null, isAnonymous = true))
        val repo = FakeClientPairingRepository()
        val viewModel = ClientPairingViewModel(repo, auth)

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
        val auth = FakeAuthService(User("u1", "u1@example.com", "User 1"))
        val repo = FakeClientPairingRepository().apply {
            submitResult = Result.failure(PairingError.InvalidOrExpiredCode)
        }
        val viewModel = ClientPairingViewModel(repo, auth)

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
        val auth = FakeAuthService(User("u1", "u1@example.com", "User 1"))
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
        val repo = FakeClientPairingRepository().apply {
            submitResult = Result.success(SubmittedRequest(pendingRequest, codeExpiresAtEpochMs = now + 600_000L))
        }
        val viewModel = ClientPairingViewModel(repo, auth, clock = { now })

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
        val auth = FakeAuthService(User("u1", "u1@example.com", "User 1"))
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
        val repo = FakeClientPairingRepository().apply {
            submitResult = Result.success(SubmittedRequest(pendingRequest, codeExpiresAtEpochMs = now + 600_000L))
        }
        val viewModel = ClientPairingViewModel(repo, auth, clock = { now })

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
        val auth = FakeAuthService(User("u1", "u1@example.com", "User 1"))
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
        val repo = FakeClientPairingRepository().apply {
            submitResult = Result.success(SubmittedRequest(pendingRequest, codeExpiresAtEpochMs = expiresAt))
        }
        val viewModel = ClientPairingViewModel(repo, auth, clock = { testScheduler.currentTime })

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
        val auth = FakeAuthService(User("u1", "u1@example.com", "User 1"))
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
        val repo = FakeClientPairingRepository().apply {
            submitDelayMs = 5000L
            submitResult = Result.success(SubmittedRequest(pendingRequest, codeExpiresAtEpochMs = now + 600_000L))
        }
        val viewModel = ClientPairingViewModel(repo, auth, clock = { now })

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
    fun removeDelegates() = runTest {
        val auth = FakeAuthService(User("u1", "u1@example.com", "User 1"))
        val repo = FakeClientPairingRepository()
        val viewModel = ClientPairingViewModel(repo, auth)

        viewModel.remove("d1")
        testScheduler.runCurrent()

        assertEquals(listOf("d1"), repo.removedDeviceIds)
    }

    @Test
    fun devicesStreamErrorSetsErrorNoCrash() = runTest {
        val auth = FakeAuthService(User("u1", "u1@example.com", "User 1"))
        val repo = FakeClientPairingRepository().apply {
            myDevicesFlow = flow { throw RuntimeException("Network error on devices stream") }
        }
        val viewModel = ClientPairingViewModel(repo, auth)
        testScheduler.runCurrent()

        assertEquals("Network error on devices stream", viewModel.devicesError.value)
        assertEquals(emptyList(), viewModel.devices.value)
    }

    @Test
    fun requestStreamErrorReturnsToEntering() = runTest {
        val now = 1000L
        val auth = FakeAuthService(User("u1", "u1@example.com", "User 1"))
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
        val repo = FakeClientPairingRepository().apply {
            submitResult = Result.success(SubmittedRequest(pendingRequest, codeExpiresAtEpochMs = now + 600_000L))
            requestFlowProvider = { flow { throw RuntimeException("Permission denied on request stream") } }
        }
        val viewModel = ClientPairingViewModel(repo, auth, clock = { now })

        viewModel.onCodeChanged("123456")
        viewModel.submit()
        testScheduler.runCurrent()

        val state = viewModel.addState.value
        assertIs<AddDeviceState.Entering>(state)
        assertEquals("123456", state.code)
        assertEquals("Permission denied on request stream", state.error)
    }

    @Test
    fun devicesResubscribeOnUserChange() = runTest {
        val userA = User("u1", "u1@example.com", "User 1")
        val userB = User("u2", "u2@example.com", "User 2")
        val auth = FakeAuthService(userA)

        val deviceA = HostDevice("d1", "Phone A", "Pixel")
        val deviceB = HostDevice("d2", "Phone B", "Samsung")

        val repo = FakeClientPairingRepository().apply {
            devicesFlowProvider = {
                val currentUid = auth.currentUser?.id
                if (currentUid == "u1") flowOf(listOf(deviceA))
                else if (currentUid == "u2") flowOf(listOf(deviceB))
                else flowOf(emptyList())
            }
        }
        val viewModel = ClientPairingViewModel(repo, auth)
        testScheduler.runCurrent()

        assertEquals(listOf(deviceA), viewModel.devices.value)

        auth.setUser(userB)
        testScheduler.runCurrent()

        assertEquals(listOf(deviceB), viewModel.devices.value)
    }

    @Test
    fun removeFailureSetsError() = runTest {
        val auth = FakeAuthService(User("u1", "u1@example.com", "User 1"))
        val repo = FakeClientPairingRepository().apply {
            removeResult = Result.failure(RuntimeException("Delete denied"))
        }
        val viewModel = ClientPairingViewModel(repo, auth)
        testScheduler.runCurrent()

        viewModel.remove("d1")
        testScheduler.runCurrent()

        assertEquals("Delete denied", viewModel.devicesError.value)
    }
}
