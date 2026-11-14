package com.dhananjay.livecast.server.check

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InMemoryCheckRateLimiterTest {

    @Test
    fun allowsAfterInterval() {
        val limiter = InMemoryCheckRateLimiter(minIntervalMs = 10_000L)

        // First call allowed
        assertTrue(limiter.tryAcquire("u1", "d1", 1_000L))

        // Second call within 10s denied
        assertFalse(limiter.tryAcquire("u1", "d1", 5_000L))

        // Different device allowed
        assertTrue(limiter.tryAcquire("u1", "d2", 5_000L))

        // Different user allowed
        assertTrue(limiter.tryAcquire("u2", "d1", 5_000L))

        // After 10s allowed
        assertTrue(limiter.tryAcquire("u1", "d1", 11_000L))
    }
}
