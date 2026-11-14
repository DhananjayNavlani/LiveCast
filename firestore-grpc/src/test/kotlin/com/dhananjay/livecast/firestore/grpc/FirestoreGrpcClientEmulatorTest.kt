package com.dhananjay.livecast.firestore.grpc

import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FirestoreGrpcClientEmulatorTest {

    private fun createClient(
        tokenProvider: suspend (forceRefresh: Boolean) -> String? = { "owner" }
    ): FirestoreGrpcClient {
        return FirestoreGrpcClient(
            config = FirestoreConnectionConfig(
                projectId = "demo-livecast",
                host = "localhost",
                port = 8080,
                plaintext = true
            ),
            tokenProvider = tokenProvider
        )
    }

    @Test
    fun commitSetThenGet() = runBlocking {
        val client = createClient()
        val docId = UUID.randomUUID().toString()
        val path = "test_docs/$docId"
        val now = Instant.ofEpochMilli(System.currentTimeMillis())

        val fields: Map<String, Any?> = mapOf(
            "s" to "x",
            "n" to 7L,
            "b" to true,
            "t" to now
        )

        client.commit(listOf(FirestoreWrite.Set(path, fields)))

        val doc = client.get(path)
        assertNotNull(doc)
        assertEquals(docId, doc.id)
        assertEquals(path, doc.path)
        assertEquals("x", doc.fields["s"])
        assertEquals(7L, doc.fields["n"])
        assertEquals(true, doc.fields["b"])
        assertEquals(now, doc.fields["t"])

        client.close()
    }

    @Test
    fun mustNotExistConflictThrows() = runBlocking {
        val client = createClient()
        val docId = UUID.randomUUID().toString()
        val path = "test_docs/$docId"

        client.commit(listOf(FirestoreWrite.Set(path, mapOf("initial" to "value"))))

        assertFailsWith<FirestoreAlreadyExistsException> {
            client.commit(listOf(FirestoreWrite.Set(path, mapOf("second" to "value"), mustNotExist = true)))
        }

        client.close()
    }

    @Test
    fun listenDocumentSeesUpdateWithin5s() = runBlocking {
        val client = createClient()
        val docId = UUID.randomUUID().toString()
        val path = "test_docs/$docId"

        client.commit(listOf(FirestoreWrite.Set(path, mapOf("ping" to 1L))))

        withTimeout(5000) {
            val flow = client.listenDocument(path)
            // Wait for initial value
            val initial = flow.filterNotNull().first()
            assertEquals(1L, initial.fields["ping"])

            // Update doc
            client.commit(listOf(FirestoreWrite.Update(path, mapOf("ping" to 2L))))

            // Wait for update
            val updated = flow.filterNotNull().first { it.fields["ping"] == 2L }
            assertEquals(2L, updated.fields["ping"])
        }

        client.close()
    }

    @Test
    fun listenDocumentEmitsNullOnDelete() = runBlocking {
        val client = createClient()
        val docId = UUID.randomUUID().toString()
        val path = "test_docs/$docId"

        client.commit(listOf(FirestoreWrite.Set(path, mapOf("key" to "value"))))

        withTimeout(5000) {
            val flow = client.listenDocument(path)
            val doc = flow.filterNotNull().first()
            assertEquals("value", doc.fields["key"])

            client.commit(listOf(FirestoreWrite.Delete(path)))

            val afterDelete = flow.first { it == null }
            assertNull(afterDelete)
        }

        client.close()
    }

    @Test
    fun listenQueryCollectionGroupWhereEquals() = runBlocking {
        val client = createClient()
        val u1 = UUID.randomUUID().toString()
        val u2 = UUID.randomUUID().toString()
        val d1 = UUID.randomUUID().toString()
        val d2 = UUID.randomUUID().toString()

        val p1 = "devices/$d1/supervisors/$u1"
        val p2 = "devices/$d2/supervisors/$u2"

        client.commit(listOf(
            FirestoreWrite.Set(p1, mapOf("uid" to u1)),
            FirestoreWrite.Set(p2, mapOf("uid" to u2))
        ))

        withTimeout(5000) {
            val query = FirestoreQuery(
                parentPath = "",
                collectionId = "supervisors",
                allDescendants = true,
                whereEquals = listOf("uid" to u1)
            )
            val results = client.listenQuery(query).first { docs -> docs.any { it.id == u1 } }
            val matching = results.filter { it.id == u1 }
            assertEquals(1, matching.size)
            assertEquals(u1, matching.first().fields["uid"])
            assertTrue(results.none { it.id == u2 })
        }

        client.close()
    }

    @Test
    fun createWithAutoIdReturnsId() = runBlocking {
        val client = createClient()
        val id = client.createWithAutoId("test_docs", mapOf("auto" to true))
        assertTrue(id.isNotBlank())

        val doc = client.get("test_docs/$id")
        assertNotNull(doc)
        assertEquals(id, doc.id)
        assertEquals(true, doc.fields["auto"])

        client.close()
    }

    @Test
    fun tokenProviderCalledPerStream() = runBlocking {
        val callCount = AtomicInteger(0)
        val client = createClient(tokenProvider = {
            callCount.incrementAndGet()
            "owner"
        })

        val docId = UUID.randomUUID().toString()
        val path = "test_docs/$docId"
        client.commit(listOf(FirestoreWrite.Set(path, mapOf("k" to "v"))))

        // 1 get
        client.get(path)

        // 2 listens
        withTimeout(5000) {
            client.listenDocument(path).filterNotNull().first()
        }
        withTimeout(5000) {
            client.listenDocument(path).filterNotNull().first()
        }

        assertTrue(callCount.get() >= 3, "Expected at least 3 token calls, got ${callCount.get()}")

        client.close()
    }

    @Test
    fun listenQueryOrderedLimited() = runBlocking {
        val client = createClient()
        val tag = UUID.randomUUID().toString()
        val p1 = "test_events/e1_$tag"
        val p2 = "test_events/e2_$tag"
        val p3 = "test_events/e3_$tag"

        client.commit(listOf(
            FirestoreWrite.Set(p1, mapOf("tag" to tag, "at" to 1000L)),
            FirestoreWrite.Set(p2, mapOf("tag" to tag, "at" to 3000L)),
            FirestoreWrite.Set(p3, mapOf("tag" to tag, "at" to 2000L))
        ))

        withTimeout(5000) {
            val query = FirestoreQuery(
                parentPath = "",
                collectionId = "test_events",
                whereEquals = listOf("tag" to tag),
                orderBy = listOf("at" to Direction.DESCENDING),
                limit = 2
            )
            val results = client.listenQuery(query).first { docs -> docs.size == 2 }
            assertEquals(2, results.size)
            assertEquals(p2, results[0].path)
            assertEquals(3000L, results[0].fields["at"])
            assertEquals(p3, results[1].path)
            assertEquals(2000L, results[1].fields["at"])
        }

        client.close()
    }
}
