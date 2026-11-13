package com.dhananjay.livecast.firestore.grpc

data class FirestoreDoc(
    val path: String,
    val id: String,
    val fields: Map<String, Any?>
)

data class FirestoreQuery(
    val parentPath: String,
    val collectionId: String,
    val allDescendants: Boolean = false,
    val whereEquals: List<Pair<String, Any?>> = emptyList()
)

sealed interface FirestoreWrite {
    data class Set(
        val path: String,
        val fields: Map<String, Any?>,
        val mustNotExist: Boolean = false
    ) : FirestoreWrite

    data class Update(
        val path: String,
        val fields: Map<String, Any?>
    ) : FirestoreWrite

    data class Delete(
        val path: String
    ) : FirestoreWrite
}

class FirestoreAlreadyExistsException(
    val path: String,
    cause: Throwable? = null
) : Exception("Document already exists at path: $path", cause)
