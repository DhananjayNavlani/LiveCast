package com.dhananjay.livecast.client.group

import com.dhananjay.livecast.firestore.grpc.Direction
import com.dhananjay.livecast.firestore.grpc.FirestoreDoc
import com.dhananjay.livecast.firestore.grpc.FirestorePermissionDeniedException
import com.dhananjay.livecast.protocol.AppEventType
import com.dhananjay.livecast.protocol.GroupFields
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DocumentStoreAppEventRepositoryTest {

    @Test
    fun queryFiltersGroupOrdersAndLimits() = runTest {
        val store = FakeDocumentStore()
        val repo = DocumentStoreAppEventRepository(store)

        store.customListenQuery = {
            flow {
                emit(emptyList())
            }
        }

        repo.observeRecent("d1", "g1", 50).first()

        assertEquals(1, store.queriedQueries.size)
        val q = store.queriedQueries.first()
        assertEquals("devices/d1", q.parentPath)
        assertEquals("appEvents", q.collectionId)
        assertEquals(false, q.allDescendants)
        assertEquals(listOf(GroupFields.GROUP_ID to "g1"), q.whereEquals)
        assertEquals(listOf(GroupFields.AT to Direction.DESCENDING), q.orderBy)
        assertEquals(50, q.limit)
    }

    @Test
    fun mapsEvents() = runTest {
        val store = FakeDocumentStore()
        val repo = DocumentStoreAppEventRepository(store)

        val validDoc = FirestoreDoc(
            path = "devices/d1/appEvents/e1",
            id = "e1",
            fields = mapOf(
                GroupFields.TYPE to "installed",
                GroupFields.PACKAGE_NAME to "com.example.app",
                GroupFields.LABEL to "Example",
                GroupFields.AT to Instant.ofEpochMilli(5000L),
                GroupFields.DETECTED_LATE to true,
                GroupFields.GROUP_ID to "g1",
            )
        )
        val invalidDoc = FirestoreDoc(
            path = "devices/d1/appEvents/e2",
            id = "e2",
            fields = mapOf(
                GroupFields.TYPE to "installed",
                // missing package name & groupId
            )
        )

        store.customListenQuery = {
            flow {
                emit(listOf(validDoc, invalidDoc))
            }
        }

        val result = repo.observeRecent("d1", "g1", 50).first()

        assertEquals(1, result.size)
        val event = result.first()
        assertEquals("e1", event.id)
        assertEquals(AppEventType.INSTALLED, event.type)
        assertEquals("com.example.app", event.packageName)
        assertEquals("Example", event.label)
        assertEquals(5000L, event.atEpochMs)
        assertEquals(true, event.detectedLate)
        assertEquals("g1", event.groupId)
    }

    @Test
    fun permissionDeniedAccessLost() = runTest {
        val store = FakeDocumentStore()
        val repo = DocumentStoreAppEventRepository(store)

        store.customListenQuery = {
            flow {
                throw FirestorePermissionDeniedException("devices/d1/appEvents")
            }
        }

        assertFailsWith<GroupError.AccessLost> {
            repo.observeRecent("d1", "g1", 50).first()
        }
    }
}
