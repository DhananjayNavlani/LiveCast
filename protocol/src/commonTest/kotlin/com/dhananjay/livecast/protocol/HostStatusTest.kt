package com.dhananjay.livecast.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HostStatusTest {

    @Test
    fun toggleFromWire() {
        assertEquals(ToggleState.ON, ToggleState.fromWire("on"))
        assertEquals(ToggleState.OFF, ToggleState.fromWire("off"))
        assertEquals(ToggleState.UNKNOWN, ToggleState.fromWire(null))
        assertEquals(ToggleState.UNKNOWN, ToggleState.fromWire("x"))
        assertEquals(ToggleState.UNKNOWN, ToggleState.fromWire(1))
    }

    @Test
    fun appEventTypeFromWire() {
        assertEquals(AppEventType.INSTALLED, AppEventType.fromWire("installed"))
        assertEquals(AppEventType.REMOVED, AppEventType.fromWire("removed"))
        assertNull(AppEventType.fromWire(null))
        assertNull(AppEventType.fromWire("other"))
        assertNull(AppEventType.fromWire(1))
    }

    @Test
    fun checkRulesConstants() {
        assertEquals(20_000L, CheckRules.REPLY_TIMEOUT_MS)
        assertEquals(30, CheckRules.FCM_TTL_SECONDS)
        assertEquals(10_000L, CheckRules.MIN_INTERVAL_MS)
        assertEquals("type", CheckRules.DATA_TYPE)
        assertEquals("checkId", CheckRules.DATA_CHECK_ID)
        assertEquals("check", CheckRules.TYPE_CHECK)
    }
}
