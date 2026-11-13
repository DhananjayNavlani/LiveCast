package com.dhananjay.livecast.firestore.grpc

import com.google.firestore.v1.CommitRequest
import com.google.firestore.v1.CreateDocumentRequest
import com.google.firestore.v1.Document
import com.google.firestore.v1.DocumentMask
import com.google.firestore.v1.FirestoreGrpc
import com.google.firestore.v1.GetDocumentRequest
import com.google.firestore.v1.ListenRequest
import com.google.firestore.v1.ListenResponse
import com.google.firestore.v1.Precondition
import com.google.firestore.v1.StructuredQuery
import com.google.firestore.v1.Target
import com.google.firestore.v1.TargetChange
import com.google.firestore.v1.Write
import com.google.protobuf.ByteString
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.Metadata
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.stub.MetadataUtils
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

private sealed interface StreamCloseReason {
    data object Unauthenticated : StreamCloseReason
    data object Unavailable : StreamCloseReason
    data object Completed : StreamCloseReason
    data class Error(val throwable: Throwable) : StreamCloseReason
}

class FirestoreGrpcClient(
    val projectId: String,
    val tokenProvider: suspend (forceRefresh: Boolean) -> String?,
    host: String = "firestore.googleapis.com",
    port: Int = 443,
    plaintext: Boolean = false,
) : AutoCloseable {

    private val grpcChannel: ManagedChannel = run {
        val builder = ManagedChannelBuilder.forAddress(host, port)
        if (plaintext) {
            builder.usePlaintext()
        } else {
            builder.useTransportSecurity()
        }
        builder.build()
    }

    private suspend fun getAuthMetadata(forceRefresh: Boolean = false): Metadata {
        val token = tokenProvider(forceRefresh)
        val databasePath = "projects/$projectId/databases/(default)"
        return Metadata().apply {
            if (!token.isNullOrBlank()) {
                put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), "Bearer $token")
            }
            put(Metadata.Key.of("google-cloud-resource-prefix", Metadata.ASCII_STRING_MARSHALLER), databasePath)
            put(Metadata.Key.of("x-goog-request-params", Metadata.ASCII_STRING_MARSHALLER), "database=$databasePath")
        }
    }

    suspend fun get(path: String): FirestoreDoc? {
        val fullPath = normalizeDocPath(path, projectId)
        val metadata = getAuthMetadata(forceRefresh = false)
        val stub = FirestoreGrpc.newBlockingStub(grpcChannel)
            .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata))
        val req = GetDocumentRequest.newBuilder()
            .setName(fullPath)
            .build()

        return try {
            val resp = withContext(Dispatchers.IO) { stub.getDocument(req) }
            toFirestoreDoc(resp, projectId)
        } catch (e: StatusRuntimeException) {
            if (e.status.code == Status.Code.NOT_FOUND) {
                null
            } else {
                throw e
            }
        }
    }

    suspend fun createWithAutoId(collectionPath: String, fields: Map<String, Any?>): String {
        val relativeCol = toRelativeDocPath(collectionPath, projectId)
        val (parent, collectionId) = if (relativeCol.contains('/')) {
            val parentRel = relativeCol.substringBeforeLast('/')
            val colId = relativeCol.substringAfterLast('/')
            "projects/$projectId/databases/(default)/documents/$parentRel" to colId
        } else {
            "projects/$projectId/databases/(default)/documents" to relativeCol
        }

        val docBuilder = Document.newBuilder()
        fields.forEach { (k, v) ->
            docBuilder.putFields(k, toProtoValue(v))
        }

        val metadata = getAuthMetadata(forceRefresh = false)
        val stub = FirestoreGrpc.newBlockingStub(grpcChannel)
            .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata))

        val req = CreateDocumentRequest.newBuilder()
            .setParent(parent)
            .setCollectionId(collectionId)
            .setDocument(docBuilder.build())
            .build()

        val resp = withContext(Dispatchers.IO) { stub.createDocument(req) }
        return resp.name.substringAfterLast('/')
    }

    suspend fun commit(writes: List<FirestoreWrite>) {
        if (writes.isEmpty()) return
        val databasePath = "projects/$projectId/databases/(default)"

        val protoWrites = writes.map { write ->
            when (write) {
                is FirestoreWrite.Set -> {
                    val fullPath = normalizeDocPath(write.path, projectId)
                    val docBuilder = Document.newBuilder().setName(fullPath)
                    write.fields.forEach { (k, v) ->
                        docBuilder.putFields(k, toProtoValue(v))
                    }
                    val wb = Write.newBuilder().setUpdate(docBuilder.build())
                    if (write.mustNotExist) {
                        wb.setCurrentDocument(Precondition.newBuilder().setExists(false).build())
                    }
                    wb.build()
                }
                is FirestoreWrite.Update -> {
                    val fullPath = normalizeDocPath(write.path, projectId)
                    val docBuilder = Document.newBuilder().setName(fullPath)
                    val maskBuilder = DocumentMask.newBuilder()
                    write.fields.forEach { (k, v) ->
                        docBuilder.putFields(k, toProtoValue(v))
                        maskBuilder.addFieldPaths(k)
                    }
                    Write.newBuilder()
                        .setUpdate(docBuilder.build())
                        .setUpdateMask(maskBuilder.build())
                        .build()
                }
                is FirestoreWrite.Delete -> {
                    val fullPath = normalizeDocPath(write.path, projectId)
                    Write.newBuilder().setDelete(fullPath).build()
                }
            }
        }

        val req = CommitRequest.newBuilder()
            .setDatabase(databasePath)
            .addAllWrites(protoWrites)
            .build()

        val metadata = getAuthMetadata(forceRefresh = false)
        val stub = FirestoreGrpc.newBlockingStub(grpcChannel)
            .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata))

        try {
            withContext(Dispatchers.IO) { stub.commit(req) }
        } catch (e: StatusRuntimeException) {
            if (e.status.code == Status.Code.ALREADY_EXISTS) {
                val conflictPath = writes.filterIsInstance<FirestoreWrite.Set>()
                    .firstOrNull { it.mustNotExist }?.path ?: ""
                throw FirestoreAlreadyExistsException(conflictPath, e)
            } else {
                throw e
            }
        }
    }

    fun listenDocument(path: String): Flow<FirestoreDoc?> = channelFlow {
        val fullDocPath = normalizeDocPath(path, projectId)
        val databasePath = "projects/$projectId/databases/(default)"

        var resumeToken: ByteString? = null
        var backoffMs = 1000L
        var forceRefreshNext = false

        while (isActive) {
            val metadata = try {
                getAuthMetadata(forceRefreshNext)
            } catch (e: Exception) {
                if (!isActive) break
                delay(backoffMs)
                backoffMs = (backoffMs * 2).coerceAtMost(30000L)
                continue
            }
            forceRefreshNext = false

            val stub = FirestoreGrpc.newStub(grpcChannel)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata))

            val streamClosed = CompletableDeferred<StreamCloseReason>()
            var activeRequestObserver: StreamObserver<ListenRequest>? = null

            var currentDoc: FirestoreDoc? = null
            var hasSeenDocInThisSnapshot = false
            var hasCurrent = false

            val responseObserver = object : StreamObserver<ListenResponse> {
                override fun onNext(resp: ListenResponse) {
                    when (resp.responseTypeCase) {
                        ListenResponse.ResponseTypeCase.TARGET_CHANGE -> {
                            val tc = resp.targetChange
                            if (tc.resumeToken != null && !tc.resumeToken.isEmpty) {
                                resumeToken = tc.resumeToken
                            }
                            when (tc.targetChangeType) {
                                TargetChange.TargetChangeType.CURRENT -> {
                                    hasCurrent = true
                                    backoffMs = 1000L
                                    if (!hasSeenDocInThisSnapshot && currentDoc == null) {
                                        trySend(null)
                                    }
                                }
                                TargetChange.TargetChangeType.NO_CHANGE -> {
                                    if (tc.hasCause()) {
                                        val status = Status.fromCodeValue(tc.cause.code)
                                        if (status.code == Status.Code.UNAUTHENTICATED) {
                                            streamClosed.complete(StreamCloseReason.Unauthenticated)
                                            return
                                        }
                                    }
                                }
                                TargetChange.TargetChangeType.REMOVE -> {
                                    val status = if (tc.hasCause()) Status.fromCodeValue(tc.cause.code) else Status.UNKNOWN
                                    if (status.code == Status.Code.UNAUTHENTICATED) {
                                        streamClosed.complete(StreamCloseReason.Unauthenticated)
                                    } else {
                                        streamClosed.complete(StreamCloseReason.Error(status.asRuntimeException()))
                                    }
                                    return
                                }
                                TargetChange.TargetChangeType.RESET -> {
                                    currentDoc = null
                                    hasSeenDocInThisSnapshot = false
                                }
                                else -> {}
                            }
                        }
                        ListenResponse.ResponseTypeCase.DOCUMENT_CHANGE -> {
                            val dc = resp.documentChange
                            val doc = toFirestoreDoc(dc.document, projectId)
                            currentDoc = doc
                            hasSeenDocInThisSnapshot = true
                            trySend(doc)
                        }
                        ListenResponse.ResponseTypeCase.DOCUMENT_DELETE,
                        ListenResponse.ResponseTypeCase.DOCUMENT_REMOVE -> {
                            currentDoc = null
                            hasSeenDocInThisSnapshot = false
                            trySend(null)
                        }
                        else -> {}
                    }
                }

                override fun onError(t: Throwable) {
                    val status = Status.fromThrowable(t)
                    when (status.code) {
                        Status.Code.UNAUTHENTICATED -> {
                            streamClosed.complete(StreamCloseReason.Unauthenticated)
                        }
                        Status.Code.UNAVAILABLE -> {
                            streamClosed.complete(StreamCloseReason.Unavailable)
                        }
                        else -> {
                            streamClosed.complete(StreamCloseReason.Error(t))
                        }
                    }
                }

                override fun onCompleted() {
                    streamClosed.complete(StreamCloseReason.Completed)
                }
            }

            try {
                val reqObserver = stub.listen(responseObserver)
                activeRequestObserver = reqObserver

                val targetBuilder = Target.newBuilder()
                    .setTargetId(1)
                    .setDocuments(
                        Target.DocumentsTarget.newBuilder()
                            .addDocuments(fullDocPath)
                            .build()
                    )
                val token = resumeToken
                if (token != null && !token.isEmpty) {
                    targetBuilder.setResumeToken(token)
                }

                val listenReq = ListenRequest.newBuilder()
                    .setDatabase(databasePath)
                    .setAddTarget(targetBuilder.build())
                    .build()

                reqObserver.onNext(listenReq)

                val reason = streamClosed.await()
                when (reason) {
                    is StreamCloseReason.Unauthenticated -> {
                        forceRefreshNext = true
                    }
                    is StreamCloseReason.Unavailable -> {
                        delay(backoffMs)
                        backoffMs = (backoffMs * 2).coerceAtMost(30000L)
                    }
                    is StreamCloseReason.Completed -> {
                        // Reopen if still active
                    }
                    is StreamCloseReason.Error -> {
                        if (isActive) {
                            close(reason.throwable)
                        }
                        break
                    }
                }
            } catch (e: CancellationException) {
                try {
                    activeRequestObserver?.onCompleted()
                } catch (_: Exception) {}
                throw e
            } catch (e: Exception) {
                val status = Status.fromThrowable(e)
                if (status.code == Status.Code.UNAUTHENTICATED) {
                    forceRefreshNext = true
                } else if (status.code == Status.Code.UNAVAILABLE) {
                    delay(backoffMs)
                    backoffMs = (backoffMs * 2).coerceAtMost(30000L)
                } else {
                    if (isActive) close(e)
                    break
                }
            } finally {
                try {
                    activeRequestObserver?.onCompleted()
                } catch (_: Exception) {}
            }
        }
    }

    fun listenQuery(query: FirestoreQuery): Flow<List<FirestoreDoc>> = channelFlow {
        val databasePath = "projects/$projectId/databases/(default)"
        val parent = if (query.parentPath.isBlank()) {
            "projects/$projectId/databases/(default)/documents"
        } else {
            "projects/$projectId/databases/(default)/documents/${toRelativeDocPath(query.parentPath, projectId)}"
        }

        var resumeToken: ByteString? = null
        var backoffMs = 1000L
        var forceRefreshNext = false

        while (isActive) {
            val metadata = try {
                getAuthMetadata(forceRefreshNext)
            } catch (e: Exception) {
                if (!isActive) break
                delay(backoffMs)
                backoffMs = (backoffMs * 2).coerceAtMost(30000L)
                continue
            }
            forceRefreshNext = false

            val stub = FirestoreGrpc.newStub(grpcChannel)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata))

            val streamClosed = CompletableDeferred<StreamCloseReason>()
            var activeRequestObserver: StreamObserver<ListenRequest>? = null

            val currentDocs = mutableMapOf<String, FirestoreDoc>()
            var hasCurrent = false
            var dirty = false

            val responseObserver = object : StreamObserver<ListenResponse> {
                override fun onNext(resp: ListenResponse) {
                    when (resp.responseTypeCase) {
                        ListenResponse.ResponseTypeCase.TARGET_CHANGE -> {
                            val tc = resp.targetChange
                            if (tc.resumeToken != null && !tc.resumeToken.isEmpty) {
                                resumeToken = tc.resumeToken
                            }
                            when (tc.targetChangeType) {
                                TargetChange.TargetChangeType.CURRENT -> {
                                    hasCurrent = true
                                    backoffMs = 1000L
                                    trySend(currentDocs.values.toList())
                                    dirty = false
                                }
                                TargetChange.TargetChangeType.NO_CHANGE -> {
                                    if (tc.hasCause()) {
                                        val status = Status.fromCodeValue(tc.cause.code)
                                        if (status.code == Status.Code.UNAUTHENTICATED) {
                                            streamClosed.complete(StreamCloseReason.Unauthenticated)
                                            return
                                        }
                                    }
                                    if (hasCurrent && dirty) {
                                        trySend(currentDocs.values.toList())
                                        dirty = false
                                    }
                                }
                                TargetChange.TargetChangeType.REMOVE -> {
                                    val status = if (tc.hasCause()) Status.fromCodeValue(tc.cause.code) else Status.UNKNOWN
                                    if (status.code == Status.Code.UNAUTHENTICATED) {
                                        streamClosed.complete(StreamCloseReason.Unauthenticated)
                                    } else {
                                        streamClosed.complete(StreamCloseReason.Error(status.asRuntimeException()))
                                    }
                                    return
                                }
                                TargetChange.TargetChangeType.RESET -> {
                                    currentDocs.clear()
                                    dirty = true
                                }
                                else -> {}
                            }
                        }
                        ListenResponse.ResponseTypeCase.DOCUMENT_CHANGE -> {
                            val dc = resp.documentChange
                            val doc = toFirestoreDoc(dc.document, projectId)
                            currentDocs[doc.path] = doc
                            dirty = true
                            if (hasCurrent) {
                                trySend(currentDocs.values.toList())
                                dirty = false
                            }
                        }
                        ListenResponse.ResponseTypeCase.DOCUMENT_DELETE -> {
                            val docName = resp.documentDelete.document
                            val relPath = toRelativeDocPath(docName, projectId)
                            currentDocs.remove(relPath)
                            dirty = true
                            if (hasCurrent) {
                                trySend(currentDocs.values.toList())
                                dirty = false
                            }
                        }
                        ListenResponse.ResponseTypeCase.DOCUMENT_REMOVE -> {
                            val docName = resp.documentRemove.document
                            val relPath = toRelativeDocPath(docName, projectId)
                            currentDocs.remove(relPath)
                            dirty = true
                            if (hasCurrent) {
                                trySend(currentDocs.values.toList())
                                dirty = false
                            }
                        }
                        else -> {}
                    }
                }

                override fun onError(t: Throwable) {
                    val status = Status.fromThrowable(t)
                    when (status.code) {
                        Status.Code.UNAUTHENTICATED -> {
                            streamClosed.complete(StreamCloseReason.Unauthenticated)
                        }
                        Status.Code.UNAVAILABLE -> {
                            streamClosed.complete(StreamCloseReason.Unavailable)
                        }
                        else -> {
                            streamClosed.complete(StreamCloseReason.Error(t))
                        }
                    }
                }

                override fun onCompleted() {
                    streamClosed.complete(StreamCloseReason.Completed)
                }
            }

            try {
                val reqObserver = stub.listen(responseObserver)
                activeRequestObserver = reqObserver

                val fromSelector = StructuredQuery.CollectionSelector.newBuilder()
                    .setCollectionId(query.collectionId)
                    .setAllDescendants(query.allDescendants)

                val sq = StructuredQuery.newBuilder()
                    .addFrom(fromSelector)

                val filters = query.whereEquals.map { (fieldPath, value) ->
                    StructuredQuery.Filter.newBuilder()
                        .setFieldFilter(
                            StructuredQuery.FieldFilter.newBuilder()
                                .setField(StructuredQuery.FieldReference.newBuilder().setFieldPath(fieldPath))
                                .setOp(StructuredQuery.FieldFilter.Operator.EQUAL)
                                .setValue(toProtoValue(value))
                        ).build()
                }

                if (filters.size == 1) {
                    sq.setWhere(filters[0])
                } else if (filters.size > 1) {
                    sq.setWhere(
                        StructuredQuery.Filter.newBuilder()
                            .setCompositeFilter(
                                StructuredQuery.CompositeFilter.newBuilder()
                                    .setOp(StructuredQuery.CompositeFilter.Operator.AND)
                                    .addAllFilters(filters)
                            )
                    )
                }

                val queryTarget = Target.QueryTarget.newBuilder()
                    .setParent(parent)
                    .setStructuredQuery(sq)
                    .build()

                val targetBuilder = Target.newBuilder()
                    .setTargetId(1)
                    .setQuery(queryTarget)

                val token = resumeToken
                if (token != null && !token.isEmpty) {
                    targetBuilder.setResumeToken(token)
                }

                val listenReq = ListenRequest.newBuilder()
                    .setDatabase(databasePath)
                    .setAddTarget(targetBuilder.build())
                    .build()

                reqObserver.onNext(listenReq)

                val reason = streamClosed.await()
                when (reason) {
                    is StreamCloseReason.Unauthenticated -> {
                        forceRefreshNext = true
                    }
                    is StreamCloseReason.Unavailable -> {
                        delay(backoffMs)
                        backoffMs = (backoffMs * 2).coerceAtMost(30000L)
                    }
                    is StreamCloseReason.Completed -> {
                        // Reopen if still active
                    }
                    is StreamCloseReason.Error -> {
                        if (isActive) {
                            close(reason.throwable)
                        }
                        break
                    }
                }
            } catch (e: CancellationException) {
                try {
                    activeRequestObserver?.onCompleted()
                } catch (_: Exception) {}
                throw e
            } catch (e: Exception) {
                val status = Status.fromThrowable(e)
                if (status.code == Status.Code.UNAUTHENTICATED) {
                    forceRefreshNext = true
                } else if (status.code == Status.Code.UNAVAILABLE) {
                    delay(backoffMs)
                    backoffMs = (backoffMs * 2).coerceAtMost(30000L)
                } else {
                    if (isActive) close(e)
                    break
                }
            } finally {
                try {
                    activeRequestObserver?.onCompleted()
                } catch (_: Exception) {}
            }
        }
    }

    override fun close() {
        try {
            grpcChannel.shutdown().awaitTermination(3, TimeUnit.SECONDS)
        } catch (_: Exception) {
            grpcChannel.shutdownNow()
        }
    }
}
