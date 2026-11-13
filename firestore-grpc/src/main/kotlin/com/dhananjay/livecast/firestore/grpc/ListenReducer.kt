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

internal class QueryListenReducer : ListenReducer<List<FirestoreDoc>> {
    private val currentDocs = mutableMapOf<String, FirestoreDoc>()
    private var hasCurrent = false
    private var dirty = false
    private var pendingSnapshot: Snapshot<List<FirestoreDoc>> = Snapshot.None

    override fun onDocumentChanged(doc: FirestoreDoc) {
        currentDocs[doc.path] = doc
        dirty = true
        if (hasCurrent) {
            pendingSnapshot = Snapshot.Emit(currentDocs.values.toList())
            dirty = false
        }
    }

    override fun onDocumentGone(path: String) {
        currentDocs.remove(path)
        dirty = true
        if (hasCurrent) {
            pendingSnapshot = Snapshot.Emit(currentDocs.values.toList())
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
        pendingSnapshot = Snapshot.Emit(currentDocs.values.toList())
        dirty = false
    }

    override fun onGlobalSnapshot() {
        if (hasCurrent && dirty) {
            pendingSnapshot = Snapshot.Emit(currentDocs.values.toList())
            dirty = false
        }
    }

    override fun takeSnapshot(): Snapshot<List<FirestoreDoc>> {
        val s = pendingSnapshot
        pendingSnapshot = Snapshot.None
        return s
    }
}
