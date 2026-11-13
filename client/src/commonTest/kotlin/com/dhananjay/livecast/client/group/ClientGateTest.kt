package com.dhananjay.livecast.client.group

import com.dhananjay.livecast.auth.User
import kotlin.test.Test
import kotlin.test.assertEquals

class ClientGateTest {

    @Test
    fun nullUserSignsIn() {
        assertEquals(ClientGate.SIGN_IN, clientGate(null))
    }

    @Test
    fun anonymousIsGuest() {
        val anon = User(id = "u1", email = null, displayName = null, isAnonymous = true)
        assertEquals(ClientGate.GUEST, clientGate(anon))

        val anonWithEmail = User(id = "u2", email = "guest@x.com", displayName = null, isAnonymous = true)
        assertEquals(ClientGate.GUEST, clientGate(anonWithEmail))

        val userNoEmail = User(id = "u3", email = null, displayName = null, isAnonymous = false)
        assertEquals(ClientGate.GUEST, clientGate(userNoEmail))

        val userBlankEmail = User(id = "u4", email = "   ", displayName = null, isAnonymous = false)
        assertEquals(ClientGate.GUEST, clientGate(userBlankEmail))
    }

    @Test
    fun unverifiedGoesToVerify() {
        val unverified = User(id = "u1", email = "user@example.com", displayName = "User", emailVerified = false)
        assertEquals(ClientGate.VERIFY_EMAIL, clientGate(unverified))
    }

    @Test
    fun verifiedIsReady() {
        val verified = User(id = "u1", email = "user@example.com", displayName = "User", emailVerified = true)
        assertEquals(ClientGate.READY, clientGate(verified))
    }
}
