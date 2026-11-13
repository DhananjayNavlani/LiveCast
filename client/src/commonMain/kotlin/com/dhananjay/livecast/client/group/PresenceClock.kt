package com.dhananjay.livecast.client.group

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

interface PresenceClock {
    fun nowEpochMs(): Long
    fun ticks(periodMs: Long): Flow<Unit>
}

class SystemPresenceClock : PresenceClock {
    override fun nowEpochMs(): Long = kotlin.time.Clock.System.now().toEpochMilliseconds()

    override fun ticks(periodMs: Long): Flow<Unit> = flow {
        while (true) {
            delay(periodMs)
            emit(Unit)
        }
    }
}
