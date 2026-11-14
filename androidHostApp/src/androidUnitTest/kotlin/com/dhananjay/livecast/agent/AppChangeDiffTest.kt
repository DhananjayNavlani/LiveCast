package com.dhananjay.livecast.agent

import com.dhananjay.livecast.protocol.AppEventType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppChangeDiffTest {

    @Test
    fun firstRunNoEvents() {
        val installed = listOf(
            InstalledApp("com.example.app", "Example App", 1000L)
        )

        val result = AppChangeDiff.onStart(
            baseline = null,
            installed = installed,
            groupId = "group1",
            nowMs = 2000L
        )

        assertTrue(result.events.isEmpty())
        assertEquals(mapOf("com.example.app" to "Example App"), result.baseline)
    }

    @Test
    fun installDetectedWithFirstInstallTime() {
        val baseline = mapOf("com.a" to "App A")
        val installed = listOf(
            InstalledApp("com.a", "App A", 1000L),
            InstalledApp("com.b", "App B", 1500L)
        )

        val result = AppChangeDiff.onStart(
            baseline = baseline,
            installed = installed,
            groupId = "group1",
            nowMs = 2000L
        )

        assertEquals(1, result.events.size)
        val event = result.events.first()
        assertEquals(AppEventType.INSTALLED, event.type)
        assertEquals("com.b", event.packageName)
        assertEquals("App B", event.label)
        assertEquals(1500L, event.atEpochMs)
        assertFalse(event.detectedLate)
        assertEquals("group1", event.groupId)
        assertEquals(mapOf("com.a" to "App A", "com.b" to "App B"), result.baseline)
    }

    @Test
    fun removalDetectedLateWithBaselineLabel() {
        val baseline = mapOf(
            "com.a" to "App A",
            "com.b" to "App B"
        )
        val installed = listOf(
            InstalledApp("com.a", "App A", 1000L)
        )

        val result = AppChangeDiff.onStart(
            baseline = baseline,
            installed = installed,
            groupId = "group1",
            nowMs = 3000L
        )

        assertEquals(1, result.events.size)
        val event = result.events.first()
        assertEquals(AppEventType.REMOVED, event.type)
        assertEquals("com.b", event.packageName)
        assertEquals("App B", event.label)
        assertEquals(3000L, event.atEpochMs)
        assertTrue(event.detectedLate)
        assertEquals("group1", event.groupId)
        assertEquals(mapOf("com.a" to "App A"), result.baseline)
    }

    @Test
    fun unchangedNoEvents() {
        val baseline = mapOf("com.a" to "App A")
        val installed = listOf(
            InstalledApp("com.a", "App A", 1000L)
        )

        val result = AppChangeDiff.onStart(
            baseline = baseline,
            installed = installed,
            groupId = "group1",
            nowMs = 2000L
        )

        assertTrue(result.events.isEmpty())
        assertEquals(baseline, result.baseline)
    }

    @Test
    fun broadcastReplacingIgnored() {
        val baseline = mapOf("com.a" to "App A")
        val change = PackageChange(
            packageName = "com.a",
            added = true,
            replacing = true,
            atEpochMs = 2000L
        )
        val app = InstalledApp("com.a", "App A", 2000L)

        val result = AppChangeDiff.onBroadcast(
            change = change,
            baseline = baseline,
            app = app,
            groupId = "group1"
        )

        assertTrue(result.events.isEmpty())
        assertEquals(baseline, result.baseline)
    }

    @Test
    fun broadcastInstallEvent() {
        val baseline = mapOf("com.a" to "App A")
        val change = PackageChange(
            packageName = "com.b",
            added = true,
            replacing = false,
            atEpochMs = 2500L
        )
        val app = InstalledApp("com.b", "App B", 2500L)

        val result = AppChangeDiff.onBroadcast(
            change = change,
            baseline = baseline,
            app = app,
            groupId = "group1"
        )

        assertEquals(1, result.events.size)
        val event = result.events.first()
        assertEquals(AppEventType.INSTALLED, event.type)
        assertEquals("com.b", event.packageName)
        assertEquals("App B", event.label)
        assertEquals(2500L, event.atEpochMs)
        assertFalse(event.detectedLate)
        assertEquals("group1", event.groupId)
        assertEquals(mapOf("com.a" to "App A", "com.b" to "App B"), result.baseline)
    }

    @Test
    fun broadcastRemovalUsesBaselineLabel() {
        val baseline = mapOf(
            "com.a" to "App A",
            "com.b" to "Saved Label B"
        )
        val change = PackageChange(
            packageName = "com.b",
            added = false,
            replacing = false,
            atEpochMs = 3000L
        )

        val result = AppChangeDiff.onBroadcast(
            change = change,
            baseline = baseline,
            app = null,
            groupId = "group1"
        )

        assertEquals(1, result.events.size)
        val event = result.events.first()
        assertEquals(AppEventType.REMOVED, event.type)
        assertEquals("com.b", event.packageName)
        assertEquals("Saved Label B", event.label)
        assertEquals(3000L, event.atEpochMs)
        assertFalse(event.detectedLate)
        assertEquals("group1", event.groupId)
        assertEquals(mapOf("com.a" to "App A"), result.baseline)
    }

    @Test
    fun broadcastUnknownRemovalIgnored() {
        val baseline = mapOf("com.a" to "App A")
        val change = PackageChange(
            packageName = "com.unknown",
            added = false,
            replacing = false,
            atEpochMs = 3000L
        )

        val result = AppChangeDiff.onBroadcast(
            change = change,
            baseline = baseline,
            app = null,
            groupId = "group1"
        )

        assertTrue(result.events.isEmpty())
        assertEquals(baseline, result.baseline)
    }
}
