package com.dhananjay.livecast.client.group

import com.dhananjay.livecast.protocol.AppEvent
import com.dhananjay.livecast.protocol.AppEventType
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class AppEventCopyTest {

    // 1700057100000 ms is 2023-11-15T14:05:00Z -> 14:05 in UTC
    private val epochMs = 1700057100000L
    private val zone = TimeZone.UTC

    @Test
    fun installedLine() {
        val event = AppEvent(
            id = "e1",
            type = AppEventType.INSTALLED,
            packageName = "com.whatsapp",
            label = "WhatsApp",
            atEpochMs = epochMs,
            detectedLate = false,
            groupId = "g1",
        )
        assertEquals("Installed WhatsApp · 14:05", AppEventCopy.line(event, zone))
    }

    @Test
    fun removedLine() {
        val event = AppEvent(
            id = "e2",
            type = AppEventType.REMOVED,
            packageName = "com.instagram",
            label = "Instagram",
            atEpochMs = epochMs,
            detectedLate = false,
            groupId = "g1",
        )
        assertEquals("Removed Instagram · 14:05", AppEventCopy.line(event, zone))
    }

    @Test
    fun lateRemovalLine() {
        val event = AppEvent(
            id = "e3",
            type = AppEventType.REMOVED,
            packageName = "com.instagram",
            label = "Instagram",
            atEpochMs = epochMs,
            detectedLate = true,
            groupId = "g1",
        )
        assertEquals("Removed Instagram · noticed 14:05", AppEventCopy.line(event, zone))
    }
}
