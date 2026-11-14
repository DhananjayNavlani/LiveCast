package com.dhananjay.livecast.client.group

import com.dhananjay.livecast.firestore.grpc.Direction
import com.dhananjay.livecast.firestore.grpc.DocumentStore
import com.dhananjay.livecast.firestore.grpc.FirestoreQuery
import com.dhananjay.livecast.protocol.AppEvent
import com.dhananjay.livecast.protocol.FirestorePaths
import com.dhananjay.livecast.protocol.GroupFields
import com.dhananjay.livecast.protocol.appEventFrom
import com.dhananjay.livecast.protocol.normalizeTimestamps
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.time.Instant

private val toMillis: (Any) -> Long? = { (it as? Instant)?.toEpochMilli() }

class DocumentStoreAppEventRepository(
    private val store: DocumentStore,
) : AppEventRepository {

    override fun observeRecent(deviceId: String, groupId: String, limit: Int): Flow<List<AppEvent>> {
        val query = FirestoreQuery(
            parentPath = FirestorePaths.device(deviceId),
            collectionId = FirestorePaths.APP_EVENTS,
            allDescendants = false,
            whereEquals = listOf(GroupFields.GROUP_ID to groupId),
            orderBy = listOf(GroupFields.AT to Direction.DESCENDING),
            limit = limit,
        )
        return store.listenQuery(query)
            .map { docs ->
                docs.mapNotNull { doc ->
                    val data = normalizeTimestamps(doc.fields, toMillis)
                    appEventFrom(doc.id, data)
                }
            }
            .catch { e ->
                throw e.toGroupError()
            }
    }
}
