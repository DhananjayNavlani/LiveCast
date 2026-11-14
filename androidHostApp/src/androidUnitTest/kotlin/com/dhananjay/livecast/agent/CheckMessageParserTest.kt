package com.dhananjay.livecast.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CheckMessageParserTest {

    @Test
    fun parsesCheck() {
        val data = mapOf("type" to "check", "checkId" to "check-123")
        assertEquals("check-123", CheckMessageParser.checkIdOf(data))
    }

    @Test
    fun otherTypeNull() {
        val data = mapOf("type" to "ping", "checkId" to "check-123")
        assertNull(CheckMessageParser.checkIdOf(data))
    }

    @Test
    fun missingIdNull() {
        assertNull(CheckMessageParser.checkIdOf(mapOf("type" to "check")))
        assertNull(CheckMessageParser.checkIdOf(mapOf("type" to "check", "checkId" to "")))
    }
}
