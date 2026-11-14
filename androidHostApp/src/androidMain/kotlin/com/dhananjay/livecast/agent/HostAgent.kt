package com.dhananjay.livecast.agent

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

interface AgentComponent {
    fun start(scope: CoroutineScope)
    fun stop(reportOff: Boolean = true)
}

interface HostAgentControl {
    fun restartIfRunning()
}

class HostAgent(
    private val components: List<AgentComponent>,
    private val appScope: CoroutineScope
) : HostAgentControl {

    private var started = false
    private var agentScope: CoroutineScope? = null

    @Synchronized
    fun start() {
        if (started) return
        started = true
        val scope = CoroutineScope(SupervisorJob(appScope.coroutineContext[Job]))
        agentScope = scope
        for (component in components) {
            try {
                component.start(scope)
            } catch (t: Throwable) {
                Log.w("HostAgent", "Failed to start component ${component::class.simpleName}", t)
            }
        }
    }

    @Synchronized
    fun stop(reportOff: Boolean = true) {
        if (!started) return
        started = false
        for (component in components) {
            try {
                component.stop(reportOff)
            } catch (t: Throwable) {
                Log.w("HostAgent", "Failed to stop component ${component::class.simpleName}", t)
            }
        }
        agentScope?.cancel()
        agentScope = null
    }

    @Synchronized
    override fun restartIfRunning() {
        if (!started) return
        stop(reportOff = false)
        start()
    }
}

class HostAgentRestartListener(
    private val agent: HostAgentControl
) : EnrollmentListener {
    override suspend fun onEnrolled(deviceId: String, groupId: String) {
        agent.restartIfRunning()
    }
}
