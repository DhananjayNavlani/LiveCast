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

    private fun typeRank(v: Any?): Int = when (v) {
        null -> 0
        is Boolean -> 1
        is Number -> 2
        is java.time.Instant, is com.google.protobuf.Timestamp, is java.util.Date -> 3
        is String -> 4
        is ByteArray -> 5
        is com.google.type.LatLng -> 7
        is List<*>, is Array<*> -> 8
        is Map<*, *> -> 9
        else -> 10
    }

    private fun isFloatingPoint(n: Number): Boolean = n is Double || n is Float

    private fun compareNumbers(n1: Number, n2: Number): Int {
        return if (isFloatingPoint(n1) || isFloatingPoint(n2)) {
            n1.toDouble().compareTo(n2.toDouble())
        } else {
            n1.toLong().compareTo(n2.toLong())
        }
    }

    private fun timestampParts(v: Any?): Pair<Long, Int> = when (v) {
        is java.time.Instant -> v.epochSecond to v.nano
        is com.google.protobuf.Timestamp -> v.seconds to v.nanos
        is java.util.Date -> (v.time / 1000L) to (((v.time % 1000L) * 1_000_000L).toInt())
        else -> 0L to 0
    }

    private fun compareTimestamps(t1: Any, t2: Any): Int {
        val (s1, n1) = timestampParts(t1)
        val (s2, n2) = timestampParts(t2)
        val secCmp = s1.compareTo(s2)
        return if (secCmp != 0) secCmp else n1.compareTo(n2)
    }

    private fun compareFieldValues(v1: Any?, v2: Any?): Int {
        val r1 = typeRank(v1)
        val r2 = typeRank(v2)
        if (r1 != r2) {
            return r1.compareTo(r2)
        }
        return when (r1) {
            0 -> 0 // null == null
            1 -> (v1 as Boolean).compareTo(v2 as Boolean)
            2 -> compareNumbers(v1 as Number, v2 as Number)
            3 -> compareTimestamps(v1!!, v2!!)
            4 -> (v1 as String).compareTo(v2 as String)
            5 -> {
                val b1 = v1 as ByteArray
                val b2 = v2 as ByteArray
                val len = minOf(b1.size, b2.size)
                for (i in 0 until len) {
                    val byte1 = b1[i].toInt() and 0xFF
                    val byte2 = b2[i].toInt() and 0xFF
                    if (byte1 != byte2) return byte1.compareTo(byte2)
                }
                b1.size.compareTo(b2.size)
            }
            7 -> {
                val g1 = v1 as com.google.type.LatLng
                val g2 = v2 as com.google.type.LatLng
                val latCmp = g1.latitude.compareTo(g2.latitude)
                if (latCmp != 0) latCmp else g1.longitude.compareTo(g2.longitude)
            }
            8 -> {
                val l1 = (v1 as? List<*>) ?: (v1 as Array<*>).toList()
                val l2 = (v2 as? List<*>) ?: (v2 as Array<*>).toList()
                val len = minOf(l1.size, l2.size)
                for (i in 0 until len) {
                    val elemCmp = compareFieldValues(l1[i], l2[i])
                    if (elemCmp != 0) return elemCmp
                }
                l1.size.compareTo(l2.size)
            }
            else -> {
                if (v1 is Comparable<*> && v1::class == v2!!::class) {
                    @Suppress("UNCHECKED_CAST")
                    (v1 as Comparable<Any>).compareTo(v2)
                } else 0
            }
        }
    }

    private fun emitList(): List<FirestoreDoc> {
        if (orderBy.isEmpty() && limit == null) {
            return currentDocs.values.toList()
        }
        var list = currentDocs.values.toList()
        if (orderBy.isNotEmpty()) {
            val lastDirection = orderBy.last().second
            list = list.sortedWith { d1, d2 ->
                for ((field, direction) in orderBy) {
                    val cmp = compareFieldValues(d1.fields[field], d2.fields[field])
                    if (cmp != 0) {
                        return@sortedWith if (direction == Direction.ASCENDING) cmp else -cmp
                    }
                }
                val tie = d1.path.compareTo(d2.path)
                if (lastDirection == Direction.ASCENDING) tie else -tie
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
        if (currentDocs.remove(path) == null) {
            val key = currentDocs.keys.firstOrNull { it == path || it.endsWith("/$path") || path.endsWith("/$it") }
            if (key != null) {
                currentDocs.remove(key)
            }
        }
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
