package com.dhananjay.livecast.client.check

import com.dhananjay.livecast.protocol.HostDevice
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CheckLogicTest {

    @Test
    fun replyBeforeTimeoutReturnsTime() = runTest {
        val flow = MutableSharedFlow<List<HostDevice>>(replay = 1)
        val dev = HostDevice(
            id = "d1",
            displayName = "Pixel",
            model = "P8",
            lastCheckId = "c1",
            lastCheckAtEpochMs = 9999L
        )
        flow.emit(listOf(dev))

        val result = awaitCheckReply(flow, "d1", "c1", 20_000L)
        assertEquals(9999L, result)
    }

    @Test
    fun timeoutReturnsNull() = runTest {
        val flow = MutableSharedFlow<List<HostDevice>>()

        var result: Long? = 123L
        val job = launch {
            result = awaitCheckReply(flow, "d1", "c1", 20_000L)
        }
        advanceTimeBy(20_001L)
        job.join()

        assertNull(result)
    }

    @Test
    fun otherCheckIdIgnored() = runTest {
        val flow = MutableSharedFlow<List<HostDevice>>(replay = 1)
        var result: Long? = null
        val job = launch {
            result = awaitCheckReply(flow, "d1", "c1", 20_000L)
        }
        runCurrent()

        // Emit device with different checkId
        flow.emit(listOf(HostDevice("d1", "Pixel", "P8", lastCheckId = "other", lastCheckAtEpochMs = 1111L)))
        runCurrent()
        assertTrue(job.isActive)
        assertNull(result)

        // Emit device with matching checkId
        flow.emit(listOf(HostDevice("d1", "Pixel", "P8", lastCheckId = "c1", lastCheckAtEpochMs = 8888L)))
        job.join()
        assertEquals(8888L, result)
    }

    @Test
    fun replyWithoutTimeUsesStatusAt() = runTest {
        val flow = MutableSharedFlow<List<HostDevice>>(replay = 1)
        val dev = HostDevice(
            id = "d1",
            displayName = "Pixel",
            model = "P8",
            lastCheckId = "c1",
            lastCheckAtEpochMs = null,
            statusAtEpochMs = 12345L,
        )
        flow.emit(listOf(dev))

        val result = awaitCheckReply(flow, "d1", "c1", 20_000L)
        assertEquals(12345L, result)
    }

    @Test
    fun replyWithoutTimeOrStatusUsesNow() = runTest {
        val flow = MutableSharedFlow<List<HostDevice>>(replay = 1)
        val dev = HostDevice(
            id = "d1",
            displayName = "Pixel",
            model = "P8",
            lastCheckId = "c1",
            lastCheckAtEpochMs = null,
            statusAtEpochMs = null,
        )
        flow.emit(listOf(dev))

        val result = awaitCheckReply(flow, "d1", "c1", 20_000L, nowMs = { 99999L })
        assertEquals(99999L, result)
    }
}
