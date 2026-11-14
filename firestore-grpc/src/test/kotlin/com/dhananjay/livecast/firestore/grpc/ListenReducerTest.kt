package com.dhananjay.livecast.firestore.grpc

import kotlin.test.Test
import kotlin.test.assertEquals

class ListenReducerTest {

    @Test
    fun documentReducerEmitsNullOnDelete() {
        val reducer = DocumentListenReducer()
        val doc = FirestoreDoc("test/1", "1", mapOf("k" to "v"))
        reducer.onDocumentChanged(doc)
        assertEquals(Snapshot.Emit(doc), reducer.takeSnapshot())

        reducer.onDocumentGone("test/1")
        assertEquals(Snapshot.Emit(null), reducer.takeSnapshot())
    }

    @Test
    fun queryReducerWithholdsUntilCurrent() {
        val reducer = QueryListenReducer()
        val doc = FirestoreDoc("test/1", "1", mapOf("k" to "v"))
        reducer.onDocumentChanged(doc)
        assertEquals(Snapshot.None, reducer.takeSnapshot())

        reducer.onCurrent()
        assertEquals(Snapshot.Emit(listOf(doc)), reducer.takeSnapshot())
    }

    @Test
    fun queryResetClearsState() {
        val reducer = QueryListenReducer()
        val doc = FirestoreDoc("test/1", "1", mapOf("k" to "v"))
        reducer.onDocumentChanged(doc)
        reducer.onCurrent()
        assertEquals(Snapshot.Emit(listOf(doc)), reducer.takeSnapshot())

        reducer.onReset()
        assertEquals(Snapshot.None, reducer.takeSnapshot())

        reducer.onCurrent()
        assertEquals(Snapshot.Emit(emptyList()), reducer.takeSnapshot())
    }

    @Test
    fun orderedAndLimited() {
        val reducer = QueryListenReducer(
            orderBy = listOf("at" to Direction.DESCENDING),
            limit = 2,
        )

        val doc1 = FirestoreDoc("test/d1", "d1", mapOf("at" to 100L))
        val doc2 = FirestoreDoc("test/d2", "d2", mapOf("at" to 300L))
        val doc3 = FirestoreDoc("test/d3", "d3", mapOf("at" to 200L))

        reducer.onDocumentChanged(doc1)
        reducer.onDocumentChanged(doc2)
        reducer.onDocumentChanged(doc3)
        reducer.onCurrent()

        val snapshot = reducer.takeSnapshot()
        assertEquals(Snapshot.Emit(listOf(doc2, doc3)), snapshot)
    }

    @Test
    fun timestampOrderingUsesNanos() {
        val reducer = QueryListenReducer(
            orderBy = listOf("ts" to Direction.DESCENDING),
            limit = 2,
        )
        val t1 = java.time.Instant.ofEpochSecond(1000L, 200)
        val t2 = java.time.Instant.ofEpochSecond(1000L, 800)
        val doc1 = FirestoreDoc("test/d1", "d1", mapOf("ts" to t1))
        val doc2 = FirestoreDoc("test/d2", "d2", mapOf("ts" to t2))

        reducer.onDocumentChanged(doc1)
        reducer.onDocumentChanged(doc2)
        reducer.onCurrent()

        assertEquals(Snapshot.Emit(listOf(doc2, doc1)), reducer.takeSnapshot())
    }

    @Test
    fun tieBreakFollowsLastDirection() {
        val reducerDesc = QueryListenReducer(
            orderBy = listOf("k" to Direction.DESCENDING),
        )
        val docA = FirestoreDoc("test/a", "a", mapOf("k" to 1L))
        val docB = FirestoreDoc("test/b", "b", mapOf("k" to 1L))

        reducerDesc.onDocumentChanged(docA)
        reducerDesc.onDocumentChanged(docB)
        reducerDesc.onCurrent()

        // With DESC, tie break is name descending -> b before a
        assertEquals(Snapshot.Emit(listOf(docB, docA)), reducerDesc.takeSnapshot())

        val reducerAsc = QueryListenReducer(
            orderBy = listOf("k" to Direction.ASCENDING),
        )
        reducerAsc.onDocumentChanged(docB)
        reducerAsc.onDocumentChanged(docA)
        reducerAsc.onCurrent()

        // With ASC, tie break is name ascending -> a before b
        assertEquals(Snapshot.Emit(listOf(docA, docB)), reducerAsc.takeSnapshot())
    }

    @Test
    fun docLeavingWindowIsRemoved() {
        val reducer = QueryListenReducer(
            orderBy = listOf("at" to Direction.DESCENDING),
            limit = 2,
        )

        val doc1 = FirestoreDoc("test/d1", "d1", mapOf("at" to 100L))
        val doc2 = FirestoreDoc("test/d2", "d2", mapOf("at" to 200L))

        reducer.onDocumentChanged(doc1)
        reducer.onDocumentChanged(doc2)
        reducer.onCurrent()
        assertEquals(Snapshot.Emit(listOf(doc2, doc1)), reducer.takeSnapshot())

        // A newer doc arrives
        val doc3 = FirestoreDoc("test/d3", "d3", mapOf("at" to 300L))
        reducer.onDocumentChanged(doc3)
        assertEquals(Snapshot.Emit(listOf(doc3, doc2)), reducer.takeSnapshot())

        // DOCUMENT_REMOVE for the oldest doc (doc1)
        reducer.onDocumentGone("test/d1")
        assertEquals(Snapshot.Emit(listOf(doc3, doc2)), reducer.takeSnapshot())
    }

    @Test
    fun mixedTypesUseTypeRank() {
        val reducerAsc = QueryListenReducer(
            orderBy = listOf("val" to Direction.ASCENDING),
        )

        val docNull = FirestoreDoc("test/null", "null", mapOf("val" to null))
        val docBool = FirestoreDoc("test/bool", "bool", mapOf("val" to true))
        val docNum = FirestoreDoc("test/num", "num", mapOf("val" to 42L))
        val docTs = FirestoreDoc("test/ts", "ts", mapOf("val" to java.time.Instant.ofEpochMilli(1000L)))
        val docStr = FirestoreDoc("test/str", "str", mapOf("val" to "hello"))

        // Add in random order
        reducerAsc.onDocumentChanged(docStr)
        reducerAsc.onDocumentChanged(docNull)
        reducerAsc.onDocumentChanged(docTs)
        reducerAsc.onDocumentChanged(docBool)
        reducerAsc.onDocumentChanged(docNum)
        reducerAsc.onCurrent()

        // Firestore rank: null < boolean < number < timestamp < string
        assertEquals(
            Snapshot.Emit(listOf(docNull, docBool, docNum, docTs, docStr)),
            reducerAsc.takeSnapshot()
        )
    }
}
