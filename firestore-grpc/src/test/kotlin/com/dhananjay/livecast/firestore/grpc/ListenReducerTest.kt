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
}
