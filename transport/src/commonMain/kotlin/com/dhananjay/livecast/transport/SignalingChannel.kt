package com.dhananjay.livecast.transport

import com.dhananjay.livecast.core.LiveCastResult
import kotlinx.coroutines.flow.Flow

interface SignalingChannel {
    suspend fun connect(sessionId: String): LiveCastResult<Unit>
    suspend fun send(message: SignalMessage): LiveCastResult<Unit>
    val incoming: Flow<SignalMessage>
    suspend fun close()
}
