package com.dhananjay.livecast.client.check

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

fun formatClock(epochMs: Long, zone: TimeZone): String {
    val local = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(zone)
    val hh = local.hour.toString().padStart(2, '0')
    val mm = local.minute.toString().padStart(2, '0')
    return "$hh:$mm"
}
