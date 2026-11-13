package com.dhananjay.livecast.firestore.grpc

import kotlinx.coroutines.flow.Flow

interface DocumentStore {
    suspend fun get(path: String): FirestoreDoc?
    fun listenDocument(path: String): Flow<FirestoreDoc?>
    fun listenQuery(query: FirestoreQuery): Flow<List<FirestoreDoc>>
    suspend fun createWithAutoId(collectionPath: String, fields: Map<String, Any?>): String
    suspend fun commit(writes: List<FirestoreWrite>)
}
