package com.dhananjay.livecast.client.pairing

import com.dhananjay.livecast.auth.User
import com.dhananjay.livecast.protocol.PairingCode
import com.dhananjay.livecast.protocol.PairingFields
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
        assertNull(fields[PairingFields.CLIENT_EMAIL])
        assertEquals("pending", fields[PairingFields.STATUS])
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
