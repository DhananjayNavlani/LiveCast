package com.dhananjay.livecast.pairing

import com.dhananjay.livecast.protocol.FirestorePaths
import com.dhananjay.livecast.protocol.HostDevice
import com.dhananjay.livecast.protocol.PairingCode
import com.dhananjay.livecast.protocol.PairingFields
import com.dhananjay.livecast.protocol.PairingRequest
import com.dhananjay.livecast.protocol.PairingRules
import com.dhananjay.livecast.protocol.PairingStatus
import com.dhananjay.livecast.protocol.Supervisor
import com.dhananjay.livecast.protocol.pairingRequestFrom
import com.dhananjay.livecast.protocol.supervisorFrom
import com.dhananjay.livecast.protocol.wire
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

interface HostPairingRepository {
    suspend fun upsertDevice(device: HostDevice): Result<Unit>
    suspend fun createCode(deviceId: String): Result<PairingCode>
    fun observePendingRequests(deviceId: String): Flow<List<PairingRequest>>
    suspend fun approve(request: PairingRequest): Result<Unit>
    suspend fun deny(request: PairingRequest): Result<Unit>
    fun observeSupervisors(deviceId: String): Flow<List<Supervisor>>
}

class FirestoreHostPairingRepository(
    private val firestore: FirebaseFirestore,
    private val codeGenerator: PairingCodeGenerator = PairingCodeGenerator(),
    private val clock: () -> Long = System::currentTimeMillis
) : HostPairingRepository {

    override suspend fun upsertDevice(device: HostDevice): Result<Unit> = runCatching {
        val docRef = firestore.document(FirestorePaths.device(device.id))
        val data = hashMapOf<String, Any?>(
            PairingFields.HOST_UID to device.id,
            PairingFields.DISPLAY_NAME to device.displayName,
            PairingFields.MODEL to device.model,
            PairingFields.ONLINE to device.online,
            PairingFields.LAST_SEEN_EPOCH_MS to device.lastSeenEpochMs,
            PairingFields.CREATED_AT to Timestamp.now()
        )
        docRef.set(data, SetOptions.merge()).await()
    }

    override suspend fun createCode(deviceId: String): Result<PairingCode> = runCatching {
        var lastError: Exception? = null
        for (attempt in 0..PairingRules.MAX_CODE_RETRIES) {
            val code = codeGenerator.next()
            val nowMs = clock()
            val nowTs = Timestamp(nowMs / 1000, ((nowMs % 1000) * 1_000_000).toInt())
            val expiresAtMs = nowMs + PairingRules.CODE_TTL_MS
            val expiresAtTs = Timestamp(expiresAtMs / 1000, ((expiresAtMs % 1000) * 1_000_000).toInt())
            val docRef = firestore.document(FirestorePaths.pairingCode(code))

            try {
                firestore.runTransaction { tx ->
                    val snapshot = tx.get(docRef)
                    if (snapshot.exists()) {
                        throw FirebaseFirestoreException(
                            "Pairing code already exists",
                            FirebaseFirestoreException.Code.ALREADY_EXISTS
                        )
                    }
                    val data = hashMapOf(
                        PairingFields.CODE to code,
                        PairingFields.DEVICE_ID to deviceId,
                        PairingFields.HOST_UID to deviceId,
                        PairingFields.CREATED_AT to nowTs,
                        PairingFields.EXPIRES_AT to expiresAtTs,
                    )
                    tx.set(docRef, data)
                }.await()

                return@runCatching PairingCode(
                    code = code,
                    deviceId = deviceId,
                    hostUid = deviceId,
                    createdAtEpochMs = nowTs.toDate().time,
                    expiresAtEpochMs = expiresAtTs.toDate().time,
                )
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("Exceeded maximum retries generating pairing code")
    }

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
                    val converted = rawData.mapValues { (_, value) ->
                        if (value is Timestamp) value.toDate().time else value
                    }
                    pairingRequestFrom(doc.id, converted)
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

    override fun observeSupervisors(deviceId: String): Flow<List<Supervisor>> = callbackFlow {
        val collectionRef = firestore.collection(FirestorePaths.DEVICES)
            .document(deviceId)
            .collection(FirestorePaths.SUPERVISORS)

        val registration = collectionRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val supervisors = snapshot.documents.mapNotNull { doc ->
                    val rawData = doc.data ?: return@mapNotNull null
                    val converted = rawData.mapValues { (_, value) ->
                        if (value is Timestamp) value.toDate().time else value
                    }
                    supervisorFrom(doc.id, converted)
                }
                trySend(supervisors)
            }
        }
        awaitClose { registration.remove() }
    }
}
