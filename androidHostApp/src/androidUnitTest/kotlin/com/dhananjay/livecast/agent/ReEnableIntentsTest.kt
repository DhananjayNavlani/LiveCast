package com.dhananjay.livecast.agent

import kotlin.test.Test
import kotlin.test.assertEquals

class ReEnableIntentsTest {

    @Test
    fun highlightExtrasUseFlattenedComponent() {
        val component = "com.dhananjay.livecast/.cast.data.services.AccessibilityService"
        val extras = ReEnableIntents.highlightExtras(component)

        assertEquals(mapOf(":settings:fragment_args_key" to component), extras)
    }
}
