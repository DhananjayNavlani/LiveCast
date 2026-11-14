@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.dhananjay.livecast.client.check

import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class ClockFormatTest {

    @Test
    fun formatsUtcHoursMinutes() {
        val instant = Instant.parse("2026-11-14T14:05:00Z")
        val formatted = formatClock(instant.toEpochMilliseconds(), TimeZone.UTC)
        assertEquals("14:05", formatted)

        val singleDigits = Instant.parse("2026-11-14T09:07:00Z")
        val formattedSingle = formatClock(singleDigits.toEpochMilliseconds(), TimeZone.UTC)
        assertEquals("09:07", formattedSingle)
    }
}
