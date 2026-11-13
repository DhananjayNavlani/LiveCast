package com.dhananjay.livecast.client.group

import com.dhananjay.livecast.auth.AuthResult
import com.dhananjay.livecast.auth.AuthState
import com.dhananjay.livecast.auth.EmailVerifier
import com.dhananjay.livecast.auth.SessionProvider
import com.dhananjay.livecast.auth.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeVerifyEmailSessionProvider(
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

private class FakeEmailVerifier : EmailVerifier {
    var sendResult: AuthResult = AuthResult.Success(User("u1", "u1@x.com", "U1"))
    var sendCallCount = 0
    var sendDelayMs = 0L

    var refreshResult: Result<Boolean> = Result.success(true)
    var refreshCallCount = 0
    var refreshDelayMs = 0L

    override suspend fun sendVerificationEmail(): AuthResult {
        sendCallCount++
        if (sendDelayMs > 0) delay(sendDelayMs)
        return sendResult
    }

    override suspend fun refreshEmailVerification(): Result<Boolean> {
        refreshCallCount++
        if (refreshDelayMs > 0) delay(refreshDelayMs)
        return refreshResult
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class VerifyEmailViewModelTest {

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
    fun sendTwiceWhileBusySendsOnce() = runTest {
        val user = User("u1", "u1@x.com", "User 1", emailVerified = false)
        val session = FakeVerifyEmailSessionProvider(user)
        val verifier = FakeEmailVerifier().apply { sendDelayMs = 500L }
        val viewModel = VerifyEmailViewModel(session, verifier)

        viewModel.send()
        viewModel.send()
        advanceUntilIdle()

        assertEquals(1, verifier.sendCallCount)
    }

    @Test
    fun sendSuccessMarksSent() = runTest {
        val user = User("u1", "u1@x.com", "User 1", emailVerified = false)
        val session = FakeVerifyEmailSessionProvider(user)
        val verifier = FakeEmailVerifier()
        val viewModel = VerifyEmailViewModel(session, verifier)

        viewModel.send()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.sent)
        assertFalse(viewModel.state.value.busy)
        assertNull(viewModel.state.value.message)
    }

    @Test
    fun checkNotVerifiedShowsHint() = runTest {
        val user = User("u1", "u1@x.com", "User 1", emailVerified = false)
        val session = FakeVerifyEmailSessionProvider(user)
        val verifier = FakeEmailVerifier().apply {
            refreshResult = Result.success(false)
        }
        val viewModel = VerifyEmailViewModel(session, verifier)

        viewModel.check()
        advanceUntilIdle()

        assertEquals(
            "Not verified yet. Open the link in your email, then try again.",
            viewModel.state.value.message
        )
        assertFalse(viewModel.state.value.busy)
    }

    @Test
    fun checkVerifiedClearsMessage() = runTest {
        val user = User("u1", "u1@x.com", "User 1", emailVerified = false)
        val session = FakeVerifyEmailSessionProvider(user)
        val verifier = FakeEmailVerifier().apply {
            refreshResult = Result.success(false)
        }
        val viewModel = VerifyEmailViewModel(session, verifier)

        viewModel.check()
        advanceUntilIdle()
        assertEquals(
            "Not verified yet. Open the link in your email, then try again.",
            viewModel.state.value.message
        )

        verifier.refreshResult = Result.success(true)
        viewModel.check()
        advanceUntilIdle()

        assertNull(viewModel.state.value.message)
        assertFalse(viewModel.state.value.busy)
    }

    @Test
    fun checkFailureShowsError() = runTest {
        val user = User("u1", "u1@x.com", "User 1", emailVerified = false)
        val session = FakeVerifyEmailSessionProvider(user)
        val verifier = FakeEmailVerifier().apply {
            refreshResult = Result.failure(IllegalStateException("Network offline"))
        }
        val viewModel = VerifyEmailViewModel(session, verifier)

        viewModel.check()
        advanceUntilIdle()

        assertEquals("Network offline", viewModel.state.value.message)
        assertFalse(viewModel.state.value.busy)
    }
}
