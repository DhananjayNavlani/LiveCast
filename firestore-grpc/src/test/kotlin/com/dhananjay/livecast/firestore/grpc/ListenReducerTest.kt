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
}
