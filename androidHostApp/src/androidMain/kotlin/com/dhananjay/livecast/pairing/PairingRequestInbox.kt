package com.dhananjay.livecast.pairing

import com.dhananjay.livecast.protocol.FirestorePaths
import com.dhananjay.livecast.protocol.PairingFields
import com.dhananjay.livecast.protocol.PairingRequest
import com.dhananjay.livecast.protocol.PairingStatus
import com.dhananjay.livecast.protocol.normalizeTimestamps
import com.dhananjay.livecast.protocol.pairingRequestFrom
import com.dhananjay.livecast.protocol.wire
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

private val toMillis: (Any) -> Long? = { (it as? Timestamp)?.toDate()?.time }

interface PairingRequestInbox {
    fun observePendingRequests(deviceId: String): Flow<List<PairingRequest>>
    suspend fun approve(request: PairingRequest): Result<Unit>
    suspend fun deny(request: PairingRequest): Result<Unit>
}

class FirestorePairingRequestInbox(
    private val firestore: FirebaseFirestore
) : PairingRequestInbox {

    override fun observePendingRequests(deviceId: String): Flow<List<PairingRequest>> = callbackFlow {
        val query = firestore.collection(FirestorePaths.PAIRING_REQUESTS)
            .whereEqualTo(PairingFields.DEVICE_ID, deviceId)
            .whereEqualTo(PairingFields.STATUS, PairingStatus.PENDING.wire())

        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val requests = snapshot.documents.mapNotNull { doc ->
                    val rawData = doc.data ?: return@mapNotNull null
                    val data = normalizeTimestamps(rawData, toMillis)
                    pairingRequestFrom(doc.id, data)
                }
                trySend(requests)
            }
        }
        awaitClose { registration.remove() }
    }

    override suspend fun approve(request: PairingRequest): Result<Unit> = runCatching {
        val supervisorRef = firestore.document(
            FirestorePaths.supervisor(request.deviceId, request.clientUid)
        )
        val supervisorSnap = supervisorRef.get().await()

        val batch = firestore.batch()

        val requestRef = firestore.document(FirestorePaths.pairingRequest(request.id))
        batch.update(
            requestRef,
            mapOf(
                PairingFields.STATUS to PairingStatus.APPROVED.wire(),
                PairingFields.DECIDED_AT to Timestamp.now(),
            )
        )

        if (!supervisorSnap.exists()) {
            val supervisorData = hashMapOf(
                PairingFields.UID to request.clientUid,
                PairingFields.NAME to request.clientName,
                PairingFields.EMAIL to request.clientEmail,
                PairingFields.REQUEST_ID to request.id,
                PairingFields.ADDED_AT to Timestamp.now(),
            )
            batch.set(supervisorRef, supervisorData)
        }

        val codeRef = firestore.document(FirestorePaths.pairingCode(request.code))
        batch.delete(codeRef)

        batch.commit().await()
    }

    override suspend fun deny(request: PairingRequest): Result<Unit> = runCatching {
        val requestRef = firestore.document(FirestorePaths.pairingRequest(request.id))
        requestRef.update(
            mapOf(
                PairingFields.STATUS to PairingStatus.DENIED.wire(),
                PairingFields.DECIDED_AT to Timestamp.now(),
            )
        ).await()
    }
}
