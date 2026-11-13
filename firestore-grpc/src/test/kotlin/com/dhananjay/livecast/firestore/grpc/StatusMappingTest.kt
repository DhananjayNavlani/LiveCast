package com.dhananjay.livecast.firestore.grpc

import io.grpc.Status
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class StatusMappingTest {

    @Test
    fun permissionDeniedMaps() {
        val e = Status.PERMISSION_DENIED.withDescription("denied").asRuntimeException()
        val mapped = mapStatusException(e, "groups/g1")
        assertIs<FirestorePermissionDeniedException>(mapped)
        assertEquals("groups/g1", mapped.path)
        assertEquals("Permission denied: groups/g1", mapped.message)
        assertSame(e, mapped.cause)
    }

    @Test
    fun otherStatusUnchanged() {
        val e = Status.NOT_FOUND.asRuntimeException()
        val unmapped = mapStatusException(e, "groups/g1")
        assertSame(e, unmapped)
    }
}
