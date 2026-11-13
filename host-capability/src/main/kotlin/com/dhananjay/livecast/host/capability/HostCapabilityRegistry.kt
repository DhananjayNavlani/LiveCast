package com.dhananjay.livecast.host.capability

import com.dhananjay.livecast.core.LiveCastError
import com.dhananjay.livecast.core.LiveCastResult
import com.dhananjay.livecast.protocol.DeviceState

object HostCapabilityRegistry {
    private var registeredClients: List<HostCapabilityClient>? = null

    fun init(clients: List<HostCapabilityClient>) {
        registeredClients = clients.toList()
    }

    fun clientsFor(capability: HostCapability): List<HostCapabilityClient> {
        return registeredClients?.filter { capability in it.supported } ?: emptyList()
    }

    suspend fun <T> executeFirst(
        capability: HostCapability,
        op: suspend (HostCapabilityClient) -> LiveCastResult<T>
    ): LiveCastResult<T> {
        val clients = registeredClients
            ?: return LiveCastResult.Failure(LiveCastError("HostCapabilityRegistry is not initialized"))
        val matching = clients.filter { capability in it.supported }
        if (matching.isEmpty()) {
            return LiveCastResult.Failure(LiveCastError("No client registered for capability: $capability"))
        }
        var lastError: LiveCastError? = null
        for (client in matching) {
            when (val result = op(client)) {
                is LiveCastResult.Success -> return result
                is LiveCastResult.Failure -> lastError = result.error
            }
        }
        return LiveCastResult.Failure(
            lastError ?: LiveCastError("All clients failed for capability: $capability")
        )
    }

    suspend fun deviceState(): LiveCastResult<DeviceState> =
        executeFirst(HostCapability.DeviceInfo) { it.deviceState() }

    suspend fun lock(): LiveCastResult<Unit> =
        executeFirst(HostCapability.Lock) { it.lock() }

    suspend fun locate(): LiveCastResult<Unit> =
        executeFirst(HostCapability.Location) { it.locate() }

    suspend fun wipe(): LiveCastResult<Unit> =
        executeFirst(HostCapability.Wipe) { it.wipe() }
}
