package com.dhananjay.livecast.protocol

import kotlin.test.Test
import kotlin.test.assertEquals

class NormalizeTimestampsTest {

    private data class DummyTimestamp(val epochMs: Long)

    @Test
    fun convertsNestedMapsAndLists() {
        val input: Map<String, Any?> = mapOf(
            "topLevel" to DummyTimestamp(100L),
            "nestedMap" to mapOf(
                "innerTime" to DummyTimestamp(200L),
                "regularStr" to "abc"
            ),
            "listWithTimes" to listOf(
                DummyTimestamp(300L),
                mapOf("mapInListTime" to DummyTimestamp(400L)),
                "plainItem"
            )
        )

        val result = normalizeTimestamps(input) { (it as? DummyTimestamp)?.epochMs }

        assertEquals(100L, result["topLevel"])
        val nested = result["nestedMap"] as Map<*, *>
        assertEquals(200L, nested["innerTime"])
        assertEquals("abc", nested["regularStr"])

        val list = result["listWithTimes"] as List<*>
        assertEquals(300L, list[0])
        val mapInList = list[1] as Map<*, *>
        assertEquals(400L, mapInList["mapInListTime"])
        assertEquals("plainItem", list[2])
    }

    @Test
    fun leavesOtherValues() {
        val input: Map<String, Any?> = mapOf(
            "str" to "hello",
            "num" to 42,
            "long" to 123456789L,
            "double" to 3.14,
            "bool" to true,
            "nullVal" to null
        )

        val result = normalizeTimestamps(input) { null }
        assertEquals("hello", result["str"])
        assertEquals(42, result["num"])
        assertEquals(123456789L, result["long"])
        assertEquals(3.14, result["double"])
        assertEquals(true, result["bool"])
        assertEquals(null, result["nullVal"])
    }
}
