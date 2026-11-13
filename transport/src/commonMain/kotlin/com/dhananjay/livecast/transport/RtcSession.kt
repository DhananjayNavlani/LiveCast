package com.dhananjay.livecast.transport

import com.dhananjay.livecast.core.LiveCastResult
import kotlinx.coroutines.flow.Flow

enum class RtcState {
    New,
    Connecting,
    Connected,
    Closed,
    Failed,
}

interface RtcSession {
    val state: Flow<RtcState>
    suspend fun start(): LiveCastResult<Unit>
    suspend fun close()
}
