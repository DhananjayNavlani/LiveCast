package com.dhananjay.livecast.agent

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class AppScopeEnrollmentNotifierTest {

    private class RecordingListener : EnrollmentListener {
        val calls = mutableListOf<Pair<String, String>>()
        override suspend fun onEnrolled(deviceId: String, groupId: String) {
            calls.add(deviceId to groupId)
        }
    }

    private class ThrowingListener : EnrollmentListener {
        override suspend fun onEnrolled(deviceId: String, groupId: String) {
            throw RuntimeException("Boom")
        }
    }

    @Test
    fun callsAllListeners() = runTest {
        val appScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val listener1 = RecordingListener()
        val listener2 = RecordingListener()
        val notifier = AppScopeEnrollmentNotifier(listOf(listener1, listener2), appScope)

        notifier.notifyEnrolled("uid1", "g1")
        advanceUntilIdle()

        assertEquals(listOf("uid1" to "g1"), listener1.calls)
        assertEquals(listOf("uid1" to "g1"), listener2.calls)
        appScope.cancel()
    }

    @Test
    fun throwingListenerDoesNotStopLater() = runTest {
        val appScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val throwing = ThrowingListener()
        val recording = RecordingListener()
        val notifier = AppScopeEnrollmentNotifier(listOf(throwing, recording), appScope)

        notifier.notifyEnrolled("uid1", "g1")
        advanceUntilIdle()

        assertEquals(listOf("uid1" to "g1"), recording.calls)
        appScope.cancel()
    }

    @Test
    fun survivesCallerCancellation() = runTest {
        val appScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val recording = RecordingListener()
        val notifier = AppScopeEnrollmentNotifier(listOf(recording), appScope)

        val callerScope = CoroutineScope(Job() + StandardTestDispatcher(testScheduler))
        callerScope.launch {
            notifier.notifyEnrolled("uid1", "g1")
        }
        runCurrent()
        callerScope.cancel()

        advanceUntilIdle()
        assertEquals(listOf("uid1" to "g1"), recording.calls)
        appScope.cancel()
    }
}
