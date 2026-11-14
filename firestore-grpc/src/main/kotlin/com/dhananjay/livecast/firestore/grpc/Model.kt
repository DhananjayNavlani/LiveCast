package com.dhananjay.livecast.firestore.grpc

enum class Direction { ASCENDING, DESCENDING }

data class FirestoreDoc(
    val path: String,
    val id: String,
    val fields: Map<String, Any?>
)

data class FirestoreQuery(
    val parentPath: String,
    val collectionId: String,
    val allDescendants: Boolean = false,
    val whereEquals: List<Pair<String, Any?>> = emptyList(),
    val orderBy: List<Pair<String, Direction>> = emptyList(),
    val limit: Int? = null,
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

class FirestorePermissionDeniedException(
    val path: String?,
    cause: Throwable? = null
) : Exception("Permission denied${path?.let { ": $it" } ?: ""}", cause)

