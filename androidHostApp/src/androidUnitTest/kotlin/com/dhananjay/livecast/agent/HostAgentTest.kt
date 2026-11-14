package com.dhananjay.livecast.agent

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class HostAgentTest {

    private class FakeComponent(
        private val shouldThrowOnStart: Boolean = false
    ) : AgentComponent {
        var startCalls = 0
        var stopCalls = 0
        var lastScope: CoroutineScope? = null

        override fun start(scope: CoroutineScope) {
            startCalls++
            lastScope = scope
            if (shouldThrowOnStart) {
                throw RuntimeException("Component start failure")
            }
        }

        override fun stop() {
            stopCalls++
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
    }

    @Test
    fun throwingComponentDoesNotStopOthers() = runTest {
        val c1 = FakeComponent(shouldThrowOnStart = true)
        val c2 = FakeComponent(shouldThrowOnStart = false)
        val agent = HostAgent(listOf(c1, c2), this)

        agent.start()

        assertEquals(1, c1.startCalls)
        assertEquals(1, c2.startCalls)
    }

    @Test
    fun stopIdempotent() = runTest {
        val c1 = FakeComponent()
        val agent = HostAgent(listOf(c1), this)

        agent.start()
        assertEquals(0, c1.stopCalls)

        agent.stop()
        assertEquals(1, c1.stopCalls)

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
    }

    @Test
    fun restartWhenNotRunningNoop() = runTest {
        val c1 = FakeComponent()
        val agent = HostAgent(listOf(c1), this)

        agent.restartIfRunning()
        assertEquals(0, c1.startCalls)
        assertEquals(0, c1.stopCalls)
    }
}
