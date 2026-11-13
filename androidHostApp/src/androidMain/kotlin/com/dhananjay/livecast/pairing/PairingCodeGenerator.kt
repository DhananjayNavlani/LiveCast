package com.dhananjay.livecast.pairing

import java.security.SecureRandom
import java.util.Locale

class PairingCodeGenerator(
    private val random: SecureRandom = SecureRandom()
) {
    fun next(): String {
        val number = random.nextInt(1_000_000)
        return String.format(Locale.US, "%06d", number)
    }
}
