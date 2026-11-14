package com.dhananjay.livecast.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReEnableIntentsTest {

    @Test
    fun highlightExtrasUseFlattenedComponent() {
        val component = "com.dhananjay.livecast/.cast.data.services.AccessibilityService"
        val extras = ReEnableIntents.highlightExtras(component)

        assertEquals(mapOf(":settings:fragment_args_key" to component), extras)
    }

    @Test
    fun freshLaunchHandles() {
        assertTrue(
            ReEnableIntents.shouldHandleReEnable(
                action = ReEnableIntents.ACTION_REENABLE_ACCESSIBILITY,
                restored = false,
                launchedFromHistory = false
            )
        )
    }

    @Test
    fun restoredIgnored() {
        assertFalse(
            ReEnableIntents.shouldHandleReEnable(
                action = ReEnableIntents.ACTION_REENABLE_ACCESSIBILITY,
                restored = true,
                launchedFromHistory = false
            )
        )
    }

    @Test
    fun fromHistoryIgnored() {
        assertFalse(
            ReEnableIntents.shouldHandleReEnable(
                action = ReEnableIntents.ACTION_REENABLE_ACCESSIBILITY,
                restored = false,
                launchedFromHistory = true
            )
        )
    }

    @Test
    fun otherActionIgnored() {
        assertFalse(
            ReEnableIntents.shouldHandleReEnable(
                action = "android.intent.action.VIEW",
                restored = false,
                launchedFromHistory = false
            )
        )
        assertFalse(
            ReEnableIntents.shouldHandleReEnable(
                action = null,
                restored = false,
                launchedFromHistory = false
            )
        )
    }
}
