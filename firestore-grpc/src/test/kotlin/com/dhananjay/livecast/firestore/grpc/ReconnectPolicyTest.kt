package com.dhananjay.livecast.firestore.grpc

import io.grpc.Status
import io.grpc.StatusRuntimeException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ReconnectPolicyTest {

    @Test
    fun firstUnauthRetriesNowWithRefresh() {
        val action = nextReconnectAction(
            reason = StreamCloseReason.Unauthenticated,
            consecutiveUnauth = 0,
            backoffMs = 1000L
        )
        assertEquals(ReconnectAction.RetryNow(forceRefresh = true), action)
    }

    @Test
    fun secondConsecutiveUnauthFails() {
        val action = nextReconnectAction(
            reason = StreamCloseReason.Unauthenticated,
            consecutiveUnauth = 1,
            backoffMs = 1000L
        )
        assertIs<ReconnectAction.Fail>(action)
        assertIs<StatusRuntimeException>(action.throwable)
        val status = Status.fromThrowable(action.throwable)
        assertEquals(Status.Code.UNAUTHENTICATED, status.code)
    }

    @Test
    fun completedBacksOff() {
        val action = nextReconnectAction(
            reason = StreamCloseReason.Completed,
            consecutiveUnauth = 0,
            backoffMs = 1000L
        )
        assertEquals(ReconnectAction.RetryAfter(delayMs = 1000L, nextBackoffMs = 2000L), action)
    }

    @Test
    fun unavailableBackoffCapsAt30s() {
        val actionAtCap = nextReconnectAction(
            reason = StreamCloseReason.Unavailable,
            consecutiveUnauth = 0,
            backoffMs = 30000L
        )
        assertEquals(ReconnectAction.RetryAfter(delayMs = 30000L, nextBackoffMs = 30000L), actionAtCap)

        val actionNearCap = nextReconnectAction(
            reason = StreamCloseReason.Unavailable,
            consecutiveUnauth = 0,
            backoffMs = 20000L
        )
        assertEquals(ReconnectAction.RetryAfter(delayMs = 20000L, nextBackoffMs = 30000L), actionNearCap)

        val actionOverCap = nextReconnectAction(
            reason = StreamCloseReason.Unavailable,
            consecutiveUnauth = 0,
            backoffMs = 45000L
        )
        assertEquals(ReconnectAction.RetryAfter(delayMs = 30000L, nextBackoffMs = 30000L), actionOverCap)
    }

    @Test
    fun errorFails() {
        val error = IllegalStateException("test error")
        val action = nextReconnectAction(
            reason = StreamCloseReason.Error(error),
            consecutiveUnauth = 0,
            backoffMs = 1000L
        )
        assertEquals(ReconnectAction.Fail(error), action)
    }
}
