package com.dhananjay.livecast.client.pairing

import com.dhananjay.livecast.auth.User
import com.dhananjay.livecast.protocol.PairingCode
import com.dhananjay.livecast.protocol.PairingFields
import com.dhananjay.livecast.protocol.PairingStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PairingRequestPolicyTest {

    @Test
    fun expiredCodeRejected() {
        val code = PairingCode(
            code = "123456",
            deviceId = "d1",
            hostUid = "h1",
            createdAtEpochMs = 1000L,
            expiresAtEpochMs = 2000L
        )

        val resultAtExpiry = PairingRequestPolicy.validateCode(code, nowEpochMs = 2000L)
        assertTrue(resultAtExpiry.isFailure)
        assertIs<PairingError.InvalidOrExpiredCode>(resultAtExpiry.exceptionOrNull())

        val resultPastExpiry = PairingRequestPolicy.validateCode(code, nowEpochMs = 2001L)
        assertTrue(resultPastExpiry.isFailure)
        assertIs<PairingError.InvalidOrExpiredCode>(resultPastExpiry.exceptionOrNull())

        val resultValid = PairingRequestPolicy.validateCode(code, nowEpochMs = 1999L)
        assertTrue(resultValid.isSuccess)
        assertEquals(code, resultValid.getOrNull())
    }

    @Test
    fun missingCodeRejected() {
        val result = PairingRequestPolicy.validateCode(null, nowEpochMs = 1000L)
        assertTrue(result.isFailure)
        assertIs<PairingError.InvalidOrExpiredCode>(result.exceptionOrNull())
    }

    @Test
    fun fieldsForUserWithoutEmail() {
        val user = User(id = "u1", email = null, displayName = "Parent")
        val code = PairingCode(
            code = "123456",
            deviceId = "d1",
            hostUid = "h1",
            createdAtEpochMs = 1000L,
            expiresAtEpochMs = 2000L
        )

        val fields = PairingRequestPolicy.requestFields(user, code)
        assertEquals("123456", fields[PairingFields.CODE])
        assertEquals("d1", fields[PairingFields.DEVICE_ID])
        assertEquals("u1", fields[PairingFields.CLIENT_UID])
        assertEquals("Parent", fields[PairingFields.CLIENT_NAME])
        assertTrue(fields.containsKey(PairingFields.CLIENT_EMAIL))
        assertNull(fields[PairingFields.CLIENT_EMAIL])
        assertEquals("pending", fields[PairingFields.STATUS])
    }

    @Test
    fun clientNameFallbacks() {
        val code = PairingCode(
            code = "123456",
            deviceId = "d1",
            hostUid = "h1",
            createdAtEpochMs = 1000L,
            expiresAtEpochMs = 2000L
        )

        // 1. displayName takes precedence
        val userWithDisplayName = User(id = "u1", email = "alice@example.com", displayName = "Alice")
        val fields1 = PairingRequestPolicy.requestFields(userWithDisplayName, code)
        assertEquals("Alice", fields1[PairingFields.CLIENT_NAME])
        val pending1 = PairingRequestPolicy.pendingRequest("req1", userWithDisplayName, code, 1000L)
        assertEquals("Alice", pending1.clientName)
        assertEquals("alice@example.com", pending1.clientEmail)
        assertEquals(PairingStatus.PENDING, pending1.status)

        // 2. Falls back to email if displayName is null
        val userWithEmailOnly = User(id = "u2", email = "bob@example.com", displayName = null)
        val fields2 = PairingRequestPolicy.requestFields(userWithEmailOnly, code)
        assertEquals("bob@example.com", fields2[PairingFields.CLIENT_NAME])
        val pending2 = PairingRequestPolicy.pendingRequest("req2", userWithEmailOnly, code, 1000L)
        assertEquals("bob@example.com", pending2.clientName)

        // 3. Falls back to "Supervisor" if both are null
        val userWithNeither = User(id = "u3", email = null, displayName = null)
        val fields3 = PairingRequestPolicy.requestFields(userWithNeither, code)
        assertEquals("Supervisor", fields3[PairingFields.CLIENT_NAME])
        val pending3 = PairingRequestPolicy.pendingRequest("req3", userWithNeither, code, 1000L)
        assertEquals("Supervisor", pending3.clientName)
    }

    @Test
    fun deviceIdParsedFromPath() {
        assertEquals("d1", PairingRequestPolicy.deviceIdFromSupervisorPath("devices/d1/supervisors/u1"))
        assertEquals(
            "d1",
            PairingRequestPolicy.deviceIdFromSupervisorPath("projects/demo/databases/(default)/documents/devices/d1/supervisors/u1")
        )
        assertNull(PairingRequestPolicy.deviceIdFromSupervisorPath("devices/d1"))
        assertNull(PairingRequestPolicy.deviceIdFromSupervisorPath("other/path"))
    }
}
