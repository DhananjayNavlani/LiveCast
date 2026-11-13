package com.dhananjay.livecast.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PairingFirestoreTest {

    @Test
    fun leadingZeroCodeIsValid() {
        assertTrue(PairingRules.isValidCode("000123"))
        assertFalse(PairingRules.isValidCode("12345"))
        assertFalse(PairingRules.isValidCode("1234567"))
        assertFalse(PairingRules.isValidCode("12a456"))
    }

    @Test
    fun pairingRequestRoundTrip() {
        val data = mapOf(
            PairingFields.CODE to "000123",
            PairingFields.DEVICE_ID to "dev-1",
            PairingFields.CLIENT_UID to "client-1",
            PairingFields.CLIENT_NAME to "Alice",
            PairingFields.CLIENT_EMAIL to null,
            PairingFields.STATUS to "approved",
            PairingFields.CREATED_AT to 1000L,
            PairingFields.DECIDED_AT to 1200L,
        )
        val request = pairingRequestFrom("req-1", data)
        assertNotNull(request)
        assertEquals("req-1", request.id)
        assertEquals("000123", request.code)
        assertEquals("dev-1", request.deviceId)
        assertEquals("client-1", request.clientUid)
        assertEquals("Alice", request.clientName)
        assertNull(request.clientEmail)
        assertEquals(PairingStatus.APPROVED, request.status)
        assertEquals(1000L, request.createdAtEpochMs)
        assertEquals(1200L, request.decidedAtEpochMs)
    }

    @Test
    fun pairingRequestUnknownStatusIsNull() {
        val data = mapOf(
            PairingFields.CODE to "000123",
            PairingFields.DEVICE_ID to "dev-1",
            PairingFields.CLIENT_UID to "client-1",
            PairingFields.CLIENT_NAME to "Alice",
            PairingFields.STATUS to "weird",
            PairingFields.CREATED_AT to 1000L,
        )
        assertNull(pairingRequestFrom("req-1", data))
    }

    @Test
    fun missingRequiredFieldIsNull() {
        val dataWithoutDeviceId = mapOf(
            PairingFields.CODE to "000123",
            PairingFields.CLIENT_UID to "client-1",
            PairingFields.CLIENT_NAME to "Alice",
            PairingFields.STATUS to "approved",
            PairingFields.CREATED_AT to 1000L,
        )
        assertNull(pairingRequestFrom("req-1", dataWithoutDeviceId))
    }

    @Test
    fun paths() {
        assertEquals("devices/d1/supervisors/u1", FirestorePaths.supervisor("d1", "u1"))
        assertEquals("devices/d1", FirestorePaths.device("d1"))
        assertEquals("pairingCodes/000123", FirestorePaths.pairingCode("000123"))
        assertEquals("pairingRequests/req-1", FirestorePaths.pairingRequest("req-1"))
    }
}
