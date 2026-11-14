package com.dhananjay.livecast.agent

import com.dhananjay.livecast.group.DeviceIdentity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PushTokenRegistryTest {

    private class FakeDeviceIdentity(override var deviceId: String? = "dev1") : DeviceIdentity

    private class FakeEnrollmentState(var groupId: String? = "grp1") : EnrollmentState {
        override suspend fun currentGroupId(): String? = groupId
    }

    private class FakePushTokenStore : PushTokenStore {
        val saved = mutableListOf<Pair<String, String>>()
        var shouldFail = false

        override suspend fun save(deviceId: String, token: String): Result<Unit> {
            if (shouldFail) {
                return Result.failure(RuntimeException("Store error"))
            }
            saved.add(deviceId to token)
            return Result.success(Unit)
        }
    }

    private class FakePushTokenSource(
        var token: String? = "src-token",
        var delayMs: Long = 0L
    ) : PushTokenSource {
        override suspend fun currentToken(): String? {
            if (delayMs > 0) {
                delay(delayMs)
            }
            return token
        }
    }

    @Test
    fun notEnrolledSkips() = runTest {
        val identity = FakeDeviceIdentity("dev1")
        val enrollment = FakeEnrollmentState(groupId = null)
        val store = FakePushTokenStore()
        val source = FakePushTokenSource()
        val registry = PushTokenRegistry(identity, enrollment, store, source)

        val result = registry.register("token1")

        assertTrue(result.isSuccess)
        assertTrue(store.saved.isEmpty())
    }

    @Test
    fun enrolledSaves() = runTest {
        val identity = FakeDeviceIdentity("dev1")
        val enrollment = FakeEnrollmentState(groupId = "grp1")
        val store = FakePushTokenStore()
        val source = FakePushTokenSource()
        val registry = PushTokenRegistry(identity, enrollment, store, source)

        val result = registry.register("token1")

        assertTrue(result.isSuccess)
        assertEquals(listOf("dev1" to "token1"), store.saved)
    }

    @Test
    fun nullTokenUsesSource() = runTest {
        val identity = FakeDeviceIdentity("dev1")
        val enrollment = FakeEnrollmentState(groupId = "grp1")
        val store = FakePushTokenStore()
        val source = FakePushTokenSource("source-token-xyz")
        val registry = PushTokenRegistry(identity, enrollment, store, source)

        val result = registry.register(null)

        assertTrue(result.isSuccess)
        assertEquals(listOf("dev1" to "source-token-xyz"), store.saved)
    }

    @Test
    fun noDeviceIdSkips() = runTest {
        val identity = FakeDeviceIdentity(null)
        val enrollment = FakeEnrollmentState(groupId = "grp1")
        val store = FakePushTokenStore()
        val source = FakePushTokenSource()
        val registry = PushTokenRegistry(identity, enrollment, store, source)

        val result = registry.register("token1")

        assertTrue(result.isSuccess)
        assertTrue(store.saved.isEmpty())
    }

    @Test
    fun onEnrolledSaves() = runTest {
        val identity = FakeDeviceIdentity("dev1")
        val enrollment = FakeEnrollmentState(groupId = "grp1")
        val store = FakePushTokenStore()
        val source = FakePushTokenSource("source-token-123")
        val registry = PushTokenRegistry(identity, enrollment, store, source)

        registry.onEnrolled("dev1", "grp1")

        assertEquals(listOf("dev1" to "source-token-123"), store.saved)
    }

    @Test
    fun sourceTimeoutFails() = runTest {
        val identity = FakeDeviceIdentity("dev1")
        val enrollment = FakeEnrollmentState(groupId = "grp1")
        val store = FakePushTokenStore()
        val source = FakePushTokenSource(delayMs = 60_000L)
        val registry = PushTokenRegistry(identity, enrollment, store, source)

        var result: Result<Unit>? = null
        val job = launch {
            result = registry.register(null)
        }
        runCurrent()
        advanceTimeBy(10_001L)
        runCurrent()

        assertTrue(result?.isFailure == true)
        assertTrue(store.saved.isEmpty())
        job.cancel()
    }
}
