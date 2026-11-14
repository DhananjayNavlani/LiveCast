package com.dhananjay.livecast.agent

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class HostAgentRestartListenerTest {

    private class FakeHostAgentControl : HostAgentControl {
        var restartCalls = 0

        override fun restartIfRunning() {
            restartCalls++
        }
    }

    @Test
    fun onEnrolledRestarts() = runTest {
        val control = FakeHostAgentControl()
        val listener = HostAgentRestartListener(control)

        listener.onEnrolled("dev1", "grp1")

        assertEquals(1, control.restartCalls)
    }
}
