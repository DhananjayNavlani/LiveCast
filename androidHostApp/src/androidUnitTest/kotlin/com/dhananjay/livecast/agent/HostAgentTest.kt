package com.dhananjay.livecast.agent

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HostAgentTest {

    private class FakeComponent(
        private val shouldThrowOnStart: Boolean = false,
        private val shouldThrowOnStop: Boolean = false
    ) : AgentComponent {
        var startCalls = 0
        var stopCalls = 0
        var lastReportOff: Boolean? = null
        var lastScope: CoroutineScope? = null

        override fun start(scope: CoroutineScope) {
            startCalls++
            lastScope = scope
            if (shouldThrowOnStart) {
                throw RuntimeException("Component start failure")
            }
        }

        override fun stop(reportOff: Boolean) {
            stopCalls++
            lastReportOff = reportOff
            if (shouldThrowOnStop) {
                throw RuntimeException("Component stop failure")
            }
        }
    }

    @Test
    fun startsAll() = runTest {
        val c1 = FakeComponent()
        val c2 = FakeComponent()
        val agent = HostAgent(listOf(c1, c2), this)

        agent.start()

        assertEquals(1, c1.startCalls)
        assertEquals(1, c2.startCalls)
        agent.stop()
    }

    @Test
    fun throwingComponentDoesNotStopOthers() = runTest {
        val c1 = FakeComponent(shouldThrowOnStart = true)
        val c2 = FakeComponent(shouldThrowOnStart = false)
        val agent = HostAgent(listOf(c1, c2), this)

        agent.start()

        assertEquals(1, c1.startCalls)
        assertEquals(1, c2.startCalls)
        agent.stop()
    }

    @Test
    fun stopIdempotent() = runTest {
        val c1 = FakeComponent()
        val agent = HostAgent(listOf(c1), this)

        agent.start()
        assertEquals(0, c1.stopCalls)

        agent.stop()
        assertEquals(1, c1.stopCalls)
        assertTrue(c1.lastReportOff == true)

        agent.stop()
        assertEquals(1, c1.stopCalls)
    }

    @Test
    fun restartIfRunningRestarts() = runTest {
        val c1 = FakeComponent()
        val agent = HostAgent(listOf(c1), this)

        agent.start()
        assertEquals(1, c1.startCalls)
        assertEquals(0, c1.stopCalls)

        agent.restartIfRunning()
        assertEquals(2, c1.startCalls)
        assertEquals(1, c1.stopCalls)
        agent.stop()
    }

    @Test
    fun restartWhenNotRunningNoop() = runTest {
        val c1 = FakeComponent()
        val agent = HostAgent(listOf(c1), this)

        agent.restartIfRunning()
        assertEquals(0, c1.startCalls)
        assertEquals(0, c1.stopCalls)
    }

    @Test
    fun restartPassesReportOffFalse() = runTest {
        val c1 = FakeComponent()
        val agent = HostAgent(listOf(c1), this)

        agent.start()
        agent.restartIfRunning()

        assertEquals(1, c1.stopCalls)
        assertFalse(c1.lastReportOff == true)
        agent.stop()
    }

    @Test
    fun throwingStopDoesNotStopOthers() = runTest {
        val c1 = FakeComponent(shouldThrowOnStop = true)
        val c2 = FakeComponent(shouldThrowOnStop = false)
        val agent = HostAgent(listOf(c1, c2), this)

        agent.start()
        agent.stop()

        assertEquals(1, c1.stopCalls)
        assertEquals(1, c2.stopCalls)
    }
}
