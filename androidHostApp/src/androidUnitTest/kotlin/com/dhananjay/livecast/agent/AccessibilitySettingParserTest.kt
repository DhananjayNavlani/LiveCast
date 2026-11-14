package com.dhananjay.livecast.agent

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccessibilitySettingParserTest {

    private val component = ComponentNameStrings(
        full = "com.dhananjay.livecast/com.dhananjay.livecast.cast.data.services.AccessibilityService",
        short = "com.dhananjay.livecast/.cast.data.services.AccessibilityService"
    )

    @Test
    fun fullMatch() {
        assertTrue(
            AccessibilitySettingParser.isEnabled(
                enabledServices = "com.dhananjay.livecast/com.dhananjay.livecast.cast.data.services.AccessibilityService",
                accessibilityEnabled = 1,
                component = component
            )
        )
    }

    @Test
    fun shortMatch() {
        assertTrue(
            AccessibilitySettingParser.isEnabled(
                enabledServices = "com.dhananjay.livecast/.cast.data.services.AccessibilityService",
                accessibilityEnabled = 1,
                component = component
            )
        )
    }

    @Test
    fun colonSeparatedListMatch() {
        val services = "other.app/.Service:com.dhananjay.livecast/.cast.data.services.AccessibilityService:third.app/.Other"
        assertTrue(
            AccessibilitySettingParser.isEnabled(
                enabledServices = services,
                accessibilityEnabled = 1,
                component = component
            )
        )
    }

    @Test
    fun similarPrefixNoMatch() {
        val services = "com.dhananjay.livecast/com.dhananjay.livecast.cast.data.services.AccessibilityService2"
        assertFalse(
            AccessibilitySettingParser.isEnabled(
                enabledServices = services,
                accessibilityEnabled = 1,
                component = component
            )
        )
    }

    @Test
    fun globalSwitchOffFalse() {
        assertFalse(
            AccessibilitySettingParser.isEnabled(
                enabledServices = "com.dhananjay.livecast/com.dhananjay.livecast.cast.data.services.AccessibilityService",
                accessibilityEnabled = 0,
                component = component
            )
        )
    }

    @Test
    fun nullFalse() {
        assertFalse(
            AccessibilitySettingParser.isEnabled(
                enabledServices = null,
                accessibilityEnabled = 1,
                component = component
            )
        )
        assertFalse(
            AccessibilitySettingParser.isEnabled(
                enabledServices = "",
                accessibilityEnabled = 1,
                component = component
            )
        )
    }
}
