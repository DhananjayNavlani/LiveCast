package com.dhananjay.livecast.firestore.grpc

internal sealed interface Snapshot<out T> {
    data class Emit<T>(val value: T) : Snapshot<T>
    data object None : Snapshot<Nothing>
}

internal interface ListenReducer<T> {
    fun onDocumentChanged(doc: FirestoreDoc)
    fun onDocumentGone(path: String)
    fun onReset()
    fun onCurrent()
    fun onGlobalSnapshot()
    fun takeSnapshot(): Snapshot<T>
}

internal class DocumentListenReducer : ListenReducer<FirestoreDoc?> {
    private var currentDoc: FirestoreDoc? = null
    private var hasSeenDocInThisSnapshot = false
    private var hasCurrent = false
    private var pendingSnapshot: Snapshot<FirestoreDoc?> = Snapshot.None

    override fun onDocumentChanged(doc: FirestoreDoc) {
        currentDoc = doc
        hasSeenDocInThisSnapshot = true
        pendingSnapshot = Snapshot.Emit(doc)
    }

    override fun onDocumentGone(path: String) {
        currentDoc = null
        hasSeenDocInThisSnapshot = false
        pendingSnapshot = Snapshot.Emit(null)
    }

    override fun onReset() {
        currentDoc = null
        hasSeenDocInThisSnapshot = false
        hasCurrent = false
        pendingSnapshot = Snapshot.None
    }

    override fun onCurrent() {
        hasCurrent = true
        if (!hasSeenDocInThisSnapshot && currentDoc == null) {
            pendingSnapshot = Snapshot.Emit(null)
        }
    }

    override fun onGlobalSnapshot() {
        // No-op for document listen
    }

    override fun takeSnapshot(): Snapshot<FirestoreDoc?> {
        val s = pendingSnapshot
        pendingSnapshot = Snapshot.None
        return s
    }
}

internal class QueryListenReducer(
    private val orderBy: List<Pair<String, Direction>> = emptyList(),
    private val limit: Int? = null,
) : ListenReducer<List<FirestoreDoc>> {
    private val currentDocs = mutableMapOf<String, FirestoreDoc>()
    private var hasCurrent = false
    private var dirty = false
    private var pendingSnapshot: Snapshot<List<FirestoreDoc>> = Snapshot.None

    private fun compareFieldValues(v1: Any?, v2: Any?): Int {
        if (v1 == null && v2 == null) return 0
        if (v1 == null) return -1
        if (v2 == null) return 1

        if (v1 is java.time.Instant && v2 is java.time.Instant) {
            return v1.compareTo(v2)
        }
        if (v1 is com.google.protobuf.Timestamp && v2 is com.google.protobuf.Timestamp) {
            val sec = v1.seconds.compareTo(v2.seconds)
            return if (sec != 0) sec else v1.nanos.compareTo(v2.nanos)
        }
        if (v1 is java.time.Instant && v2 is com.google.protobuf.Timestamp) {
            val sec = v1.epochSecond.compareTo(v2.seconds)
            return if (sec != 0) sec else v1.nano.compareTo(v2.nanos)
        }
        if (v1 is com.google.protobuf.Timestamp && v2 is java.time.Instant) {
            val sec = v1.seconds.compareTo(v2.epochSecond)
            return if (sec != 0) sec else v1.nanos.compareTo(v2.nano)
        }
        if (v1 is java.util.Date && v2 is java.util.Date) {
            return v1.compareTo(v2)
        }
        if (v1 is java.time.Instant && v2 is java.util.Date) {
            return v1.compareTo(v2.toInstant())
        }
        if (v1 is java.util.Date && v2 is java.time.Instant) {
            return v1.toInstant().compareTo(v2)
        }
        if (v1 is Number && v2 is Number) {
            return v1.toDouble().compareTo(v2.toDouble())
        }
        if (v1 is String && v2 is String) {
            return v1.compareTo(v2)
        }
        if (v1 is Comparable<*> && v1::class == v2::class) {
            @Suppress("UNCHECKED_CAST")
            return (v1 as Comparable<Any>).compareTo(v2)
        }
        return 0
    }

    private fun emitList(): List<FirestoreDoc> {
        if (orderBy.isEmpty() && limit == null) {
            return currentDocs.values.toList()
        }
        var list = currentDocs.values.toList()
        if (orderBy.isNotEmpty()) {
            list = list.sortedWith { d1, d2 ->
                for ((field, direction) in orderBy) {
                    val cmp = compareFieldValues(d1.fields[field], d2.fields[field])
                    if (cmp != 0) {
                        return@sortedWith if (direction == Direction.ASCENDING) cmp else -cmp
                    }
                }
                d1.path.compareTo(d2.path)
            }
        }
        if (limit != null && limit >= 0) {
            list = list.take(limit)
        }
        return list
    }

    override fun onDocumentChanged(doc: FirestoreDoc) {
        currentDocs[doc.path] = doc
        dirty = true
        if (hasCurrent) {
            pendingSnapshot = Snapshot.Emit(emitList())
            dirty = false
        }
    }

    override fun onDocumentGone(path: String) {
        currentDocs.remove(path)
        dirty = true
        if (hasCurrent) {
            pendingSnapshot = Snapshot.Emit(emitList())
            dirty = false
        }
    }

    override fun onReset() {
        currentDocs.clear()
        hasCurrent = false
        dirty = true
        pendingSnapshot = Snapshot.None
    }

    override fun onCurrent() {
        hasCurrent = true
        pendingSnapshot = Snapshot.Emit(emitList())
        dirty = false
    }

    override fun onGlobalSnapshot() {
        if (hasCurrent && dirty) {
            pendingSnapshot = Snapshot.Emit(emitList())
            dirty = false
        }
    }

    override fun takeSnapshot(): Snapshot<List<FirestoreDoc>> {
        val s = pendingSnapshot
        pendingSnapshot = Snapshot.None
        return s
    }
}
