package com.dhananjay.livecast.firestore.grpc

import com.google.firestore.v1.StructuredQuery
import kotlin.test.Test
import kotlin.test.assertEquals

class QueryMappingTest {

    @Test
    fun orderByAndLimitMapped() {
        val query = FirestoreQuery(
            parentPath = "devices/d1",
            collectionId = "appEvents",
            whereEquals = listOf("groupId" to "g1"),
            orderBy = listOf("at" to Direction.DESCENDING),
            limit = 50,
        )

        val sq = buildStructuredQuery(query)

        assertEquals("appEvents", sq.fromList.first().collectionId)
        assertEquals(false, sq.fromList.first().allDescendants)
        // where
        assertEquals(true, sq.hasWhere())
        assertEquals("groupId", sq.where.fieldFilter.field.fieldPath)
        assertEquals("g1", sq.where.fieldFilter.value.stringValue)
        // orderBy
        assertEquals(1, sq.orderByCount)
        assertEquals("at", sq.getOrderBy(0).field.fieldPath)
        assertEquals(StructuredQuery.Direction.DESCENDING, sq.getOrderBy(0).direction)
        // limit
        assertEquals(50, sq.limit.value)
    }
}
