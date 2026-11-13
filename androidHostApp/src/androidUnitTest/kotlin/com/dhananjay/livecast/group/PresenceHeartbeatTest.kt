package com.dhananjay.livecast.group

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PresenceHeartbeatTest {

    private class FakePresenceReporter : PresenceReporter {
        val reports = mutableListOf<Pair<String, Boolean>>()
        var shouldFail = false

        override suspend fun report(deviceId: String, online: Boolean): Result<Unit> {
            reports.add(deviceId to online)
            return if (shouldFail) {
                Result.failure(RuntimeException("Report error"))
            } else {
                Result.success(Unit)
            }
        }
    }

    @Test
    fun reportsOnlineImmediatelyAndEveryInterval() = runTest {
        val reporter = FakePresenceReporter()
        val heartbeat = PresenceHeartbeat(reporter, intervalMs = 45_000L)

        val job = launch(StandardTestDispatcher(testScheduler)) {
            heartbeat.run("d1")
        }
        runCurrent()
        assertEquals(1, reporter.reports.size)
        assertEquals("d1" to true, reporter.reports[0])

        advanceTimeBy(45_000L)
        runCurrent()
        assertEquals(2, reporter.reports.size)
        assertEquals("d1" to true, reporter.reports[1])

        job.cancel()
    }

    @Test
    fun failedReportKeepsLooping() = runTest {
        val reporter = FakePresenceReporter()
        reporter.shouldFail = true
        val heartbeat = PresenceHeartbeat(reporter, intervalMs = 45_000L)

        val job = launch(StandardTestDispatcher(testScheduler)) {
            heartbeat.run("d1")
        }
        runCurrent()
        assertEquals(1, reporter.reports.size)

        advanceTimeBy(45_000L)
        runCurrent()
        assertEquals(2, reporter.reports.size)

        job.cancel()
    }

    @Test
    fun cancelReportsOffline() = runTest {
        val reporter = FakePresenceReporter()
        val heartbeat = PresenceHeartbeat(reporter, intervalMs = 45_000L)

        val job = launch(StandardTestDispatcher(testScheduler)) {
            heartbeat.run("d1")
        }
        runCurrent()
        assertEquals(listOf("d1" to true), reporter.reports)

        job.cancel()
        runCurrent()

        assertEquals(2, reporter.reports.size)
        assertEquals("d1" to false, reporter.reports.last())
    }

    @Test
    fun hangingReportDoesNotStallLoop() = runTest {
        val reporter = object : PresenceReporter {
            var calls = 0
            override suspend fun report(deviceId: String, online: Boolean): Result<Unit> {
                calls++
                awaitCancellation()
            }
        }
        val heartbeat = PresenceHeartbeat(reporter, intervalMs = 45_000L)

        val job = launch(StandardTestDispatcher(testScheduler)) {
            heartbeat.run("d1")
        }
        runCurrent()
        assertEquals(1, reporter.calls)

        advanceTimeBy(2 * 45_000L)
        runCurrent()
        assertTrue(reporter.calls >= 3, "Expected at least 3 calls, got ${reporter.calls}")

        job.cancel()
    }
}
