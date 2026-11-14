package com.dhananjay.livecast.agent

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppBaselineResetListenerTest {

    private class FakeAppBaselineStore : AppBaselineStore {
        var clearCalls = 0
        var currentBaseline: Map<String, String>? = null

        override suspend fun load(): Map<String, String>? = currentBaseline
        override suspend fun save(baseline: Map<String, String>) {
            currentBaseline = baseline
        }
        override suspend fun clear() {
            clearCalls++
            currentBaseline = null
        }
    }

    @Test
    fun onEnrolledClears() = runTest {
        val store = FakeAppBaselineStore()
        store.save(mapOf("com.example.app" to "Example App"))
        val listener = AppBaselineResetListener(store)

        listener.onEnrolled("device-1", "group-1")

        assertEquals(1, store.clearCalls)
        assertNull(store.load())
    }
}
