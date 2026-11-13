package com.dhananjay.livecast.pairing

import java.security.SecureRandom
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PairingCodeGeneratorTest {

    private class StubSecureRandom(private val fixedInt: Int) : SecureRandom() {
        override fun nextInt(bound: Int): Int = fixedInt
    }

    @Test
    fun generatorKeepsLeadingZeros() {
        val stub = StubSecureRandom(123)
        val generator = PairingCodeGenerator(stub)
        assertEquals("000123", generator.next())

        val realGenerator = PairingCodeGenerator()
        val regex = Regex("^[0-9]{6}$")
        repeat(1000) {
            val code = realGenerator.next()
            assertTrue(regex.matches(code), "Expected 6 digits but got '$code'")
        }
    }
}
