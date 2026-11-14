package com.dhananjay.livecast.client.group

import com.dhananjay.livecast.client.check.formatClock
import com.dhananjay.livecast.protocol.AppEvent
import com.dhananjay.livecast.protocol.AppEventType
import kotlinx.datetime.TimeZone

object AppEventCopy {
    fun line(event: AppEvent, zone: TimeZone): String {
        val time = formatClock(event.atEpochMs, zone)
        return when (event.type) {
            AppEventType.INSTALLED -> "Installed ${event.label} · $time"
            AppEventType.REMOVED -> {
                if (event.detectedLate) {
                    "Removed ${event.label} · noticed $time"
                } else {
                    "Removed ${event.label} · $time"
                }
            }
        }
    }
}
