package com.dhananjay.livecast.group

import com.dhananjay.livecast.agent.EnrollmentNotifier
import com.dhananjay.livecast.auth.AuthResult
import com.dhananjay.livecast.auth.AuthState
import com.dhananjay.livecast.auth.EmailVerifier
import com.dhananjay.livecast.auth.SessionProvider
import com.dhananjay.livecast.auth.User
import com.dhananjay.livecast.protocol.Group
import com.dhananjay.livecast.protocol.GroupMember
import com.dhananjay.livecast.protocol.GroupRole
import com.dhananjay.livecast.protocol.HostDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class EnrollmentViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeSessionProvider(
        initialUser: User? = null
    ) : SessionProvider {
        val authStateFlow = MutableStateFlow<AuthState>(
            initialUser?.let { AuthState.Authenticated(it) } ?: AuthState.Unauthenticated
        )
        override val authState: StateFlow<AuthState> = authStateFlow.asStateFlow()
        override val currentUser: User?
            get() = (authState.value as? AuthState.Authenticated)?.user

        override suspend fun getIdToken(forceRefresh: Boolean): String? = "fake-token"
    }

    private class FakeEmailVerifier(
        private val session: FakeSessionProvider? = null
    ) : EmailVerifier {
        var refreshResult: Result<Boolean> = Result.success(true)
        var sendResult: AuthResult = AuthResult.Success(User("uid", "email", "name"))
        var refreshCalls = 0
        var sendCalls = 0

        override suspend fun sendVerificationEmail(): AuthResult {
            sendCalls++
            return sendResult
        }

        override suspend fun refreshEmailVerification(): Result<Boolean> {
            refreshCalls++
            val verified = refreshResult.getOrDefault(false)
            if (verified && session != null) {
                val current = session.currentUser
                if (current != null) {
                    session.authStateFlow.value = AuthState.Authenticated(current.copy(emailVerified = true))
                }
            }
            return refreshResult
        }
    }

    private class FakeHostDeviceInfoProvider : HostDeviceInfoProvider {
        override fun current(deviceId: String): HostDevice {
            return HostDevice(
                id = deviceId,
                displayName = "Pixel 8",
                model = "Pixel 8"
            )
        }
    }

    private class FakeEnrollmentDirectory : EnrollmentDirectory {
        var candidatesResult: Result<List<EnrollmentCandidate>> = Result.success(emptyList())
        val queriedEmails = mutableListOf<String>()

        override suspend fun candidates(email: String): Result<List<EnrollmentCandidate>> {
            queriedEmails.add(email)
            return candidatesResult
        }
    }

    private class FakeDeviceRegistry : DeviceRegistry {
        val deviceFlows = mutableMapOf<String, MutableSharedFlow<HostDevice?>>()
        val observedDeviceIds = mutableListOf<String>()
        val cancelledDeviceIds = mutableListOf<String>()
        val enrollCalls = mutableListOf<Pair<HostDevice, String>>()
        var enrollResult: Result<Unit> = Result.success(Unit)
        var enrollDelayMs = 0L
        var customFlowForDevice: ((String) -> Flow<HostDevice?>)? = null

        fun flowFor(deviceId: String): MutableSharedFlow<HostDevice?> {
            return deviceFlows.getOrPut(deviceId) { MutableSharedFlow(replay = 1) }
        }

        override fun observeDevice(deviceId: String): Flow<HostDevice?> = flow {
            observedDeviceIds.add(deviceId)
            try {
                val custom = customFlowForDevice?.invoke(deviceId)
                if (custom != null) {
                    custom.collect { emit(it) }
                } else {
                    flowFor(deviceId).collect { emit(it) }
                }
            } finally {
                cancelledDeviceIds.add(deviceId)
            }
        }

        override suspend fun enroll(device: HostDevice, groupId: String): Result<Unit> {
            if (enrollDelayMs > 0L) {
                kotlinx.coroutines.delay(enrollDelayMs)
            }
            enrollCalls.add(device to groupId)
            return enrollResult
        }
    }

    @Test
    fun alreadyEnrolledShowsEnrolled() = runTest {
        val user = User(id = "uid1", email = "kid@x.com", displayName = "Kid", emailVerified = true)
        val session = FakeSessionProvider(user)
        val verifier = FakeEmailVerifier(session)
        val deviceInfo = FakeHostDeviceInfoProvider()
        val directory = FakeEnrollmentDirectory()
        val registry = FakeDeviceRegistry()

        registry.flowFor("uid1").tryEmit(
            HostDevice(id = "uid1", displayName = "Pixel 8", model = "Pixel 8", groupId = "g1")
        )

        val vm = EnrollmentViewModel(session, verifier, deviceInfo, directory, registry)
        advanceUntilIdle()

        assertEquals(EnrollmentUiState.Enrolled("g1"), vm.state.value)
    }

    @Test
    fun anonymousShowsNoEmail() = runTest {
        val user = User(id = "uid1", email = null, displayName = "Guest", isAnonymous = true)
        val session = FakeSessionProvider(user)
        val verifier = FakeEmailVerifier(session)
        val deviceInfo = FakeHostDeviceInfoProvider()
        val directory = FakeEnrollmentDirectory()
        val registry = FakeDeviceRegistry()

        registry.flowFor("uid1").tryEmit(
            HostDevice(id = "uid1", displayName = "Pixel 8", model = "Pixel 8", groupId = null)
        )

        val vm = EnrollmentViewModel(session, verifier, deviceInfo, directory, registry)
        advanceUntilIdle()

        assertEquals(EnrollmentUiState.NoEmail, vm.state.value)
    }

    @Test
    fun unverifiedShowsVerifyEmail() = runTest {
        val user = User(id = "uid1", email = "kid@x.com", displayName = "Kid", emailVerified = false)
        val session = FakeSessionProvider(user)
        val verifier = FakeEmailVerifier(session)
        val deviceInfo = FakeHostDeviceInfoProvider()
        val directory = FakeEnrollmentDirectory()
        val registry = FakeDeviceRegistry()

        registry.flowFor("uid1").tryEmit(
            HostDevice(id = "uid1", displayName = "Pixel 8", model = "Pixel 8", groupId = null)
        )

        val vm = EnrollmentViewModel(session, verifier, deviceInfo, directory, registry)
        advanceUntilIdle()

        assertEquals(EnrollmentUiState.VerifyEmail(email = "kid@x.com"), vm.state.value)
    }

    @Test
    fun checkVerificationReloadsCandidates() = runTest {
        val user = User(id = "uid1", email = "kid@x.com", displayName = "Kid", emailVerified = false)
        val session = FakeSessionProvider(user)
        val verifier = FakeEmailVerifier(session)
        val deviceInfo = FakeHostDeviceInfoProvider()
        val directory = FakeEnrollmentDirectory()
        val registry = FakeDeviceRegistry()

        val candidate = EnrollmentCandidate(
            group = Group(id = "g1", name = "Family", ownerUid = "o1", createdAtEpochMs = 1000L),
            supervisors = listOf(
                GroupMember("g1", "mum@x.com", GroupRole.OWNER, "Mum", 100L)
            )
        )
        directory.candidatesResult = Result.success(listOf(candidate))
        verifier.refreshResult = Result.success(true)

        registry.flowFor("uid1").tryEmit(
            HostDevice(id = "uid1", displayName = "Pixel 8", model = "Pixel 8", groupId = null)
        )

        val vm = EnrollmentViewModel(session, verifier, deviceInfo, directory, registry)
        advanceUntilIdle()

        assertTrue(vm.state.value is EnrollmentUiState.VerifyEmail)

        vm.checkVerification()
        advanceUntilIdle()

        assertEquals(1, verifier.refreshCalls)
        assertEquals(listOf("kid@x.com"), directory.queriedEmails)
        assertEquals(EnrollmentUiState.Confirm(candidate, canGoBack = false), vm.state.value)
    }

    @Test
    fun checkVerificationLoadsCandidatesOnce() = runTest {
        val user = User(id = "uid1", email = "kid@x.com", displayName = "Kid", emailVerified = false)
        val session = FakeSessionProvider(user)
        val verifier = FakeEmailVerifier(session)
        val deviceInfo = FakeHostDeviceInfoProvider()
        val directory = FakeEnrollmentDirectory()
        val registry = FakeDeviceRegistry()

        val candidate = EnrollmentCandidate(
            group = Group(id = "g1", name = "Family", ownerUid = "o1", createdAtEpochMs = 1000L),
            supervisors = listOf(
                GroupMember("g1", "mum@x.com", GroupRole.OWNER, "Mum", 100L)
            )
        )
        directory.candidatesResult = Result.success(listOf(candidate))
        verifier.refreshResult = Result.success(true)

        registry.flowFor("uid1").tryEmit(
            HostDevice(id = "uid1", displayName = "Pixel 8", model = "Pixel 8", groupId = null)
        )

        val vm = EnrollmentViewModel(session, verifier, deviceInfo, directory, registry)
        advanceUntilIdle()

        vm.checkVerification()
        advanceUntilIdle()

        assertEquals(1, directory.queriedEmails.size)
        assertEquals("kid@x.com", directory.queriedEmails[0])
    }

    @Test
    fun checkVerificationStillUnverified() = runTest {
        val user = User(id = "uid1", email = "kid@x.com", displayName = "Kid", emailVerified = false)
        val session = FakeSessionProvider(user)
        val verifier = FakeEmailVerifier(session)
        val deviceInfo = FakeHostDeviceInfoProvider()
        val directory = FakeEnrollmentDirectory()
        val registry = FakeDeviceRegistry()

        verifier.refreshResult = Result.success(false)

        registry.flowFor("uid1").tryEmit(
            HostDevice(id = "uid1", displayName = "Pixel 8", model = "Pixel 8", groupId = null)
        )

        val vm = EnrollmentViewModel(session, verifier, deviceInfo, directory, registry)
        advanceUntilIdle()

        vm.checkVerification()
        advanceUntilIdle()

        val state = vm.state.value as EnrollmentUiState.VerifyEmail
        assertEquals("Not verified yet. Open the link in your email, then try again.", state.message)
        assertFalse(state.busy)
    }

    @Test
    fun noMembershipShowsNotMember() = runTest {
        val user = User(id = "uid1", email = "kid@x.com", displayName = "Kid", emailVerified = true)
        val session = FakeSessionProvider(user)
        val verifier = FakeEmailVerifier(session)
        val deviceInfo = FakeHostDeviceInfoProvider()
        val directory = FakeEnrollmentDirectory()
        val registry = FakeDeviceRegistry()

        directory.candidatesResult = Result.success(emptyList())
        registry.flowFor("uid1").tryEmit(
            HostDevice(id = "uid1", displayName = "Pixel 8", model = "Pixel 8", groupId = null)
        )

        val vm = EnrollmentViewModel(session, verifier, deviceInfo, directory, registry)
        advanceUntilIdle()

        assertEquals(EnrollmentUiState.NotMember("kid@x.com"), vm.state.value)
    }

    @Test
    fun singleCandidateShowsConfirm() = runTest {
        val user = User(id = "uid1", email = "kid@x.com", displayName = "Kid", emailVerified = true)
        val session = FakeSessionProvider(user)
        val verifier = FakeEmailVerifier(session)
        val deviceInfo = FakeHostDeviceInfoProvider()
        val directory = FakeEnrollmentDirectory()
        val registry = FakeDeviceRegistry()

        val supervisors = listOf(
            GroupMember("g1", "mum@x.com", GroupRole.OWNER, "Mum", 100L),
            GroupMember("g1", "dad@x.com", GroupRole.SUPERVISOR, "Dad", 200L)
        )
        val candidate = EnrollmentCandidate(
            group = Group(id = "g1", name = "Family", ownerUid = "mum-uid", createdAtEpochMs = 1000L),
            supervisors = supervisors
        )
        directory.candidatesResult = Result.success(listOf(candidate))
        registry.flowFor("uid1").tryEmit(
            HostDevice(id = "uid1", displayName = "Pixel 8", model = "Pixel 8", groupId = null)
        )

        val vm = EnrollmentViewModel(session, verifier, deviceInfo, directory, registry)
        advanceUntilIdle()

        val expected = EnrollmentUiState.Confirm(candidate, canGoBack = false)
        assertEquals(expected, vm.state.value)
        assertEquals(supervisors, (vm.state.value as EnrollmentUiState.Confirm).candidate.supervisors)
    }

    @Test
    fun multipleCandidatesShowsPicker() = runTest {
        val user = User(id = "uid1", email = "kid@x.com", displayName = "Kid", emailVerified = true)
        val session = FakeSessionProvider(user)
        val verifier = FakeEmailVerifier(session)
        val deviceInfo = FakeHostDeviceInfoProvider()
        val directory = FakeEnrollmentDirectory()
        val registry = FakeDeviceRegistry()

        val candidate1 = EnrollmentCandidate(
            group = Group(id = "g1", name = "Family", ownerUid = "o1", createdAtEpochMs = 1000L),
            supervisors = listOf(GroupMember("g1", "mum@x.com", GroupRole.OWNER, "Mum", 100L))
        )
        val candidate2 = EnrollmentCandidate(
            group = Group(id = "g2", name = "Work", ownerUid = "o2", createdAtEpochMs = 2000L),
            supervisors = listOf(GroupMember("g2", "boss@x.com", GroupRole.OWNER, "Boss", 200L))
        )
        val candidates = listOf(candidate1, candidate2)
        directory.candidatesResult = Result.success(candidates)
        registry.flowFor("uid1").tryEmit(
            HostDevice(id = "uid1", displayName = "Pixel 8", model = "Pixel 8", groupId = null)
        )

        val vm = EnrollmentViewModel(session, verifier, deviceInfo, directory, registry)
        advanceUntilIdle()

        assertEquals(EnrollmentUiState.ChooseGroup(candidates), vm.state.value)

        vm.choose("g2")
        assertEquals(EnrollmentUiState.Confirm(candidate2, canGoBack = true), vm.state.value)

        vm.back()
        assertEquals(EnrollmentUiState.ChooseGroup(candidates), vm.state.value)
    }

    @Test
    fun confirmEnrollsWithGroupId() = runTest {
        val user = User(id = "uid1", email = "kid@x.com", displayName = "Kid", emailVerified = true)
        val session = FakeSessionProvider(user)
        val verifier = FakeEmailVerifier(session)
        val deviceInfo = FakeHostDeviceInfoProvider()
        val directory = FakeEnrollmentDirectory()
        val registry = FakeDeviceRegistry()

        val candidate = EnrollmentCandidate(
            group = Group(id = "g1", name = "Family", ownerUid = "o1", createdAtEpochMs = 1000L),
            supervisors = listOf(GroupMember("g1", "mum@x.com", GroupRole.OWNER, "Mum", 100L))
        )
        directory.candidatesResult = Result.success(listOf(candidate))
        registry.flowFor("uid1").tryEmit(
            HostDevice(id = "uid1", displayName = "Pixel 8", model = "Pixel 8", groupId = null)
        )

        val vm = EnrollmentViewModel(session, verifier, deviceInfo, directory, registry)
        advanceUntilIdle()

        vm.confirm()
        advanceUntilIdle()

        assertEquals(1, registry.enrollCalls.size)
        val (device, groupId) = registry.enrollCalls[0]
        assertEquals("g1", groupId)
        val expectedDevice = deviceInfo.current(user.id)
        assertEquals(expectedDevice, device)
        assertEquals("Pixel 8", device.displayName)
        assertEquals("Pixel 8", device.model)
    }

    @Test
    fun confirmIgnoredWhileBusy() = runTest {
        val user = User(id = "uid1", email = "kid@x.com", displayName = "Kid", emailVerified = true)
        val session = FakeSessionProvider(user)
        val verifier = FakeEmailVerifier(session)
        val deviceInfo = FakeHostDeviceInfoProvider()
        val directory = FakeEnrollmentDirectory()
        val registry = FakeDeviceRegistry()
        registry.enrollDelayMs = 1000L

        val candidate = EnrollmentCandidate(
            group = Group(id = "g1", name = "Family", ownerUid = "o1", createdAtEpochMs = 1000L),
            supervisors = listOf(GroupMember("g1", "mum@x.com", GroupRole.OWNER, "Mum", 100L))
        )
        directory.candidatesResult = Result.success(listOf(candidate))
        registry.flowFor("uid1").tryEmit(
            HostDevice(id = "uid1", displayName = "Pixel 8", model = "Pixel 8", groupId = null)
        )

        val vm = EnrollmentViewModel(session, verifier, deviceInfo, directory, registry)
        advanceUntilIdle()

        vm.confirm()
        // Second call while busy
        vm.confirm()
        advanceUntilIdle()

        assertEquals(1, registry.enrollCalls.size)
    }

    @Test
    fun enrollFailureShowsError() = runTest {
        val user = User(id = "uid1", email = "kid@x.com", displayName = "Kid", emailVerified = true)
        val session = FakeSessionProvider(user)
        val verifier = FakeEmailVerifier(session)
        val deviceInfo = FakeHostDeviceInfoProvider()
        val directory = FakeEnrollmentDirectory()
        val registry = FakeDeviceRegistry()
        registry.enrollResult = Result.failure(RuntimeException("Permission denied"))

        val candidate = EnrollmentCandidate(
            group = Group(id = "g1", name = "Family", ownerUid = "o1", createdAtEpochMs = 1000L),
            supervisors = listOf(GroupMember("g1", "mum@x.com", GroupRole.OWNER, "Mum", 100L))
        )
        directory.candidatesResult = Result.success(listOf(candidate))
        registry.flowFor("uid1").tryEmit(
            HostDevice(id = "uid1", displayName = "Pixel 8", model = "Pixel 8", groupId = null)
        )

        val vm = EnrollmentViewModel(session, verifier, deviceInfo, directory, registry)
        advanceUntilIdle()

        vm.confirm()
        advanceUntilIdle()

        val state = vm.state.value as EnrollmentUiState.Confirm
        assertEquals(candidate, state.candidate)
        assertFalse(state.busy)
        assertEquals("Permission denied", state.error)
    }

    @Test
    fun removalReturnsToNotEnrolled() = runTest {
        val user = User(id = "uid1", email = "kid@x.com", displayName = "Kid", emailVerified = true)
        val session = FakeSessionProvider(user)
        val verifier = FakeEmailVerifier(session)
        val deviceInfo = FakeHostDeviceInfoProvider()
        val directory = FakeEnrollmentDirectory()
        val registry = FakeDeviceRegistry()

        directory.candidatesResult = Result.success(emptyList())

        // Initial device state: enrolled in g1
        registry.flowFor("uid1").tryEmit(
            HostDevice(id = "uid1", displayName = "Pixel 8", model = "Pixel 8", groupId = "g1")
        )

        val vm = EnrollmentViewModel(session, verifier, deviceInfo, directory, registry)
        advanceUntilIdle()

        assertEquals(EnrollmentUiState.Enrolled("g1"), vm.state.value)

        // Removal: owner clears groupId
        registry.flowFor("uid1").tryEmit(
            HostDevice(id = "uid1", displayName = "Pixel 8", model = "Pixel 8", groupId = null)
        )
        advanceUntilIdle()

        // Leaves Enrolled, directory queried again
        assertTrue(directory.queriedEmails.contains("kid@x.com"))
        assertEquals(EnrollmentUiState.NotMember("kid@x.com"), vm.state.value)
    }

    @Test
    fun directoryFailureShowsErrorAndRetryRecovers() = runTest {
        val user = User(id = "uid1", email = "kid@x.com", displayName = "Kid", emailVerified = true)
        val session = FakeSessionProvider(user)
        val verifier = FakeEmailVerifier(session)
        val deviceInfo = FakeHostDeviceInfoProvider()
        val directory = FakeEnrollmentDirectory()
        val registry = FakeDeviceRegistry()

        directory.candidatesResult = Result.failure(RuntimeException("Network failure"))
        registry.flowFor("uid1").tryEmit(
            HostDevice(id = "uid1", displayName = "Pixel 8", model = "Pixel 8", groupId = null)
        )

        val vm = EnrollmentViewModel(session, verifier, deviceInfo, directory, registry)
        advanceUntilIdle()

        assertEquals(EnrollmentUiState.Error("Network failure"), vm.state.value)

        // Retry recovers
        val candidate = EnrollmentCandidate(
            group = Group(id = "g1", name = "Family", ownerUid = "o1", createdAtEpochMs = 1000L),
            supervisors = listOf(GroupMember("g1", "mum@x.com", GroupRole.OWNER, "Mum", 100L))
        )
        directory.candidatesResult = Result.success(listOf(candidate))
        vm.retry()
        advanceUntilIdle()

        assertEquals(EnrollmentUiState.Confirm(candidate, canGoBack = false), vm.state.value)
    }

    @Test
    fun userSwitchRebinds() = runTest {
        val userA = User(id = "user-a", email = "a@x.com", displayName = "User A", emailVerified = true)
        val userB = User(id = "user-b", email = "b@x.com", displayName = "User B", emailVerified = true)
        val session = FakeSessionProvider(userA)
        val verifier = FakeEmailVerifier(session)
        val deviceInfo = FakeHostDeviceInfoProvider()
        val directory = FakeEnrollmentDirectory()
        val registry = FakeDeviceRegistry()

        registry.flowFor("user-a").tryEmit(
            HostDevice(id = "user-a", displayName = "Pixel 8", model = "Pixel 8", groupId = "g1")
        )
        registry.flowFor("user-b").tryEmit(
            HostDevice(id = "user-b", displayName = "Pixel 8", model = "Pixel 8", groupId = "g2")
        )

        val vm = EnrollmentViewModel(session, verifier, deviceInfo, directory, registry)
        advanceUntilIdle()

        assertEquals(EnrollmentUiState.Enrolled("g1"), vm.state.value)
        assertTrue("user-a" in registry.observedDeviceIds)

        // Switch to user B
        session.authStateFlow.value = AuthState.Authenticated(userB)
        advanceUntilIdle()

        assertTrue("user-a" in registry.cancelledDeviceIds)
        assertTrue("user-b" in registry.observedDeviceIds)
        assertEquals(EnrollmentUiState.Enrolled("g2"), vm.state.value)
    }

    @Test
    fun deviceListenerErrorShowsError() = runTest {
        val user = User(id = "uid1", email = "kid@x.com", displayName = "Kid", emailVerified = true)
        val session = FakeSessionProvider(user)
        val verifier = FakeEmailVerifier(session)
        val deviceInfo = FakeHostDeviceInfoProvider()
        val directory = FakeEnrollmentDirectory()
        val registry = FakeDeviceRegistry()

        var shouldThrow = true
        registry.customFlowForDevice = {
            flow {
                if (shouldThrow) {
                    throw RuntimeException("Device listener failed")
                } else {
                    emit(HostDevice(id = "uid1", displayName = "Pixel 8", model = "Pixel 8", groupId = null))
                }
            }
        }

        val vm = EnrollmentViewModel(session, verifier, deviceInfo, directory, registry)
        advanceUntilIdle()

        assertEquals(EnrollmentUiState.Error("Device listener failed"), vm.state.value)

        // Retry with healthy flow -> recovers
        shouldThrow = false
        val candidate = EnrollmentCandidate(
            group = Group(id = "g1", name = "Family", ownerUid = "o1", createdAtEpochMs = 1000L),
            supervisors = listOf(GroupMember("g1", "mum@x.com", GroupRole.OWNER, "Mum", 100L))
        )
        directory.candidatesResult = Result.success(listOf(candidate))
        vm.retry()
        advanceUntilIdle()

        assertEquals(EnrollmentUiState.Confirm(candidate, canGoBack = false), vm.state.value)
    }

    @Test
    fun retryTwiceRunsOneLookupAtATime() = runTest {
        val user = User(id = "uid1", email = "kid@x.com", displayName = "Kid", emailVerified = true)
        val session = FakeSessionProvider(user)
        val verifier = FakeEmailVerifier(session)
        val deviceInfo = FakeHostDeviceInfoProvider()
        val registry = FakeDeviceRegistry()

        var concurrentLookups = 0
        var maxConcurrent = 0
        var lookupCalls = 0
        val directory = object : EnrollmentDirectory {
            override suspend fun candidates(email: String): Result<List<EnrollmentCandidate>> {
                lookupCalls++
                concurrentLookups++
                if (concurrentLookups > maxConcurrent) maxConcurrent = concurrentLookups
                try {
                    delay(100L)
                    return Result.success(emptyList())
                } finally {
                    concurrentLookups--
                }
            }
        }

        registry.flowFor("uid1").tryEmit(
            HostDevice(id = "uid1", displayName = "Pixel 8", model = "Pixel 8", groupId = null)
        )

        val vm = EnrollmentViewModel(session, verifier, deviceInfo, directory, registry)
        advanceUntilIdle()

        vm.retry()
        runCurrent()
        vm.retry()
        runCurrent()
        advanceUntilIdle()

        assertEquals(1, maxConcurrent)
        assertEquals(3, lookupCalls)
        assertEquals(EnrollmentUiState.NotMember("kid@x.com"), vm.state.value)
    }

    @Test
    fun userSwitchDropsStaleCandidates() = runTest {
        val userA = User(id = "user-a", email = "a@x.com", displayName = "User A", emailVerified = true)
        val userB = User(id = "user-b", email = "b@x.com", displayName = "User B", emailVerified = true)
        val session = FakeSessionProvider(userA)
        val verifier = FakeEmailVerifier(session)
        val deviceInfo = FakeHostDeviceInfoProvider()
        val registry = FakeDeviceRegistry()

        val candidateA = EnrollmentCandidate(
            group = Group(id = "g-a", name = "Group A", ownerUid = "o-a", createdAtEpochMs = 1000L),
            supervisors = listOf(GroupMember("g-a", "oa@x.com", GroupRole.OWNER, "Owner A", 100L))
        )
        val candidateB = EnrollmentCandidate(
            group = Group(id = "g-b", name = "Group B", ownerUid = "o-b", createdAtEpochMs = 2000L),
            supervisors = listOf(GroupMember("g-b", "ob@x.com", GroupRole.OWNER, "Owner B", 200L))
        )

        val directory = object : EnrollmentDirectory {
            override suspend fun candidates(email: String): Result<List<EnrollmentCandidate>> {
                if (email == "a@x.com") {
                    delay(500L)
                    return Result.success(listOf(candidateA))
                } else {
                    return Result.success(listOf(candidateB))
                }
            }
        }

        registry.flowFor("user-a").tryEmit(
            HostDevice(id = "user-a", displayName = "Pixel 8", model = "Pixel 8", groupId = null)
        )
        registry.flowFor("user-b").tryEmit(
            HostDevice(id = "user-b", displayName = "Pixel 8", model = "Pixel 8", groupId = null)
        )

        val vm = EnrollmentViewModel(session, verifier, deviceInfo, directory, registry)
        advanceTimeBy(100L)
        session.authStateFlow.value = AuthState.Authenticated(userB)
        advanceUntilIdle()

        assertEquals(EnrollmentUiState.Confirm(candidateB, canGoBack = false), vm.state.value)
    }

    private class FakeEnrollmentNotifier(
        private val shouldThrow: Boolean = false
    ) : EnrollmentNotifier {
        val calls = mutableListOf<Pair<String, String>>()
        override fun notifyEnrolled(deviceId: String, groupId: String) {
            calls.add(deviceId to groupId)
            if (shouldThrow) {
                throw RuntimeException("notifier failure")
            }
        }
    }

    @Test
    fun confirmNotifiesListeners() = runTest {
        val user = User(id = "uid1", email = "kid@x.com", displayName = "Kid", emailVerified = true)
        val session = FakeSessionProvider(user)
        val verifier = FakeEmailVerifier(session)
        val deviceInfo = FakeHostDeviceInfoProvider()
        val directory = FakeEnrollmentDirectory()
        val registry = FakeDeviceRegistry()
        val notifier = FakeEnrollmentNotifier()

        val candidate = EnrollmentCandidate(
            group = Group(id = "g1", name = "Family", ownerUid = "o1", createdAtEpochMs = 1000L),
            supervisors = listOf(GroupMember("g1", "mum@x.com", GroupRole.OWNER, "Mum", 100L))
        )
        directory.candidatesResult = Result.success(listOf(candidate))
        registry.flowFor("uid1").tryEmit(
            HostDevice(id = "uid1", displayName = "Pixel 8", model = "Pixel 8", groupId = null)
        )

        val vm = EnrollmentViewModel(session, verifier, deviceInfo, directory, registry, notifier)
        advanceUntilIdle()

        vm.confirm()
        advanceUntilIdle()

        assertEquals(EnrollmentUiState.Enrolled("g1"), vm.state.value)
        assertEquals(listOf("uid1" to "g1"), notifier.calls)
    }

    @Test
    fun listenerFailureStillEnrolled() = runTest {
        val user = User(id = "uid1", email = "kid@x.com", displayName = "Kid", emailVerified = true)
        val session = FakeSessionProvider(user)
        val verifier = FakeEmailVerifier(session)
        val deviceInfo = FakeHostDeviceInfoProvider()
        val directory = FakeEnrollmentDirectory()
        val registry = FakeDeviceRegistry()
        val notifier = FakeEnrollmentNotifier(shouldThrow = true)

        val candidate = EnrollmentCandidate(
            group = Group(id = "g1", name = "Family", ownerUid = "o1", createdAtEpochMs = 1000L),
            supervisors = listOf(GroupMember("g1", "mum@x.com", GroupRole.OWNER, "Mum", 100L))
        )
        directory.candidatesResult = Result.success(listOf(candidate))
        registry.flowFor("uid1").tryEmit(
            HostDevice(id = "uid1", displayName = "Pixel 8", model = "Pixel 8", groupId = null)
        )

        val vm = EnrollmentViewModel(session, verifier, deviceInfo, directory, registry, notifier)
        advanceUntilIdle()

        vm.confirm()
        advanceUntilIdle()

        assertEquals(EnrollmentUiState.Enrolled("g1"), vm.state.value)
        assertEquals(listOf("uid1" to "g1"), notifier.calls)
    }
}
