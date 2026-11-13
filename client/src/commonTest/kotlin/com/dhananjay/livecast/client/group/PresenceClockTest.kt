package com.dhananjay.livecast.client.group

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class PresenceClockTest {

    @Test
    fun systemClockTicksEveryPeriod() = runTest {
        val clock = SystemPresenceClock()
        val emissions = mutableListOf<Unit>()

        backgroundScope.launch {
            clock.ticks(15_000L).collect {
                emissions.add(it)
            }
        }

        advanceTimeBy(15_001L)
        assertEquals(1, emissions.size)

        advanceTimeBy(15_000L)
        assertEquals(2, emissions.size)
    }
}
