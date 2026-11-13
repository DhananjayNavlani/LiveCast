package com.dhananjay.livecast.client.pairing

import com.dhananjay.livecast.auth.AuthService
import com.dhananjay.livecast.protocol.FirestorePaths
import com.dhananjay.livecast.protocol.HostDevice
import com.dhananjay.livecast.protocol.PairingFields
import com.dhananjay.livecast.protocol.PairingRequest
import com.dhananjay.livecast.protocol.PairingStatus
import com.dhananjay.livecast.protocol.hostDeviceFrom
import com.dhananjay.livecast.protocol.pairingCodeFrom
import com.dhananjay.livecast.protocol.pairingRequestFrom
import com.dhananjay.livecast.protocol.wire
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AndroidClientPairingRepository(
    private val authService: AuthService,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : ClientPairingRepository {

    override suspend fun submitCode(code: String): Result<SubmittedRequest> {
        val user = authService.currentUser ?: return Result.failure(PairingError.NotSignedIn)
        if (user.isAnonymous) return Result.failure(PairingError.AnonymousNotAllowed)

        return runCatching {
            val codeSnap = firestore.document(FirestorePaths.pairingCode(code)).get().await()
            if (!codeSnap.exists()) {
                throw PairingError.InvalidOrExpiredCode
            }

            val codeData = convertTimestamps(codeSnap.data ?: emptyMap())
            val pairingCode = pairingCodeFrom(code, codeData) ?: throw PairingError.InvalidOrExpiredCode

            val nowMs = System.currentTimeMillis()
            if (nowMs >= pairingCode.expiresAtEpochMs) {
                throw PairingError.InvalidOrExpiredCode
            }

            val requestData = hashMapOf(
                PairingFields.CODE to code,
                PairingFields.DEVICE_ID to pairingCode.deviceId,
                PairingFields.CLIENT_UID to user.id,
                PairingFields.CLIENT_NAME to (user.displayName ?: user.email ?: "Supervisor"),
                PairingFields.CLIENT_EMAIL to user.email,
                PairingFields.STATUS to PairingStatus.PENDING.wire(),
                PairingFields.CREATED_AT to Timestamp.now()
            )

            val ref = firestore.collection(FirestorePaths.PAIRING_REQUESTS).add(requestData).await()
            val request = PairingRequest(
                id = ref.id,
                code = code,
                deviceId = pairingCode.deviceId,
                clientUid = user.id,
                clientName = user.displayName ?: user.email ?: "Supervisor",
                clientEmail = user.email,
                status = PairingStatus.PENDING,
                createdAtEpochMs = nowMs,
                decidedAtEpochMs = null
            )
            SubmittedRequest(request, pairingCode.expiresAtEpochMs)
        }
    }

    override fun observeRequest(requestId: String): Flow<PairingRequest?> = callbackFlow {
        val docRef = firestore.document(FirestorePaths.pairingRequest(requestId))
        val registration = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                val data = convertTimestamps(snapshot.data ?: emptyMap())
                trySend(pairingRequestFrom(snapshot.id, data))
            } else {
                trySend(null)
            }
        }
        awaitClose { registration.remove() }
    }

    override fun observeMyDevices(): Flow<List<HostDevice>> = callbackFlow {
        val user = authService.currentUser
        if (user == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val query = firestore.collectionGroup(FirestorePaths.SUPERVISORS)
            .whereEqualTo(PairingFields.UID, user.id)

        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                launch {
                    val deviceIds = snapshot.documents.mapNotNull { doc ->
                        doc.reference.parent?.parent?.id
                    }.distinct()

                    val devices = deviceIds.mapNotNull { deviceId ->
                        try {
                            val devSnap = firestore.document(FirestorePaths.device(deviceId)).get().await()
                            if (devSnap.exists()) {
                                val data = convertTimestamps(devSnap.data ?: emptyMap())
                                hostDeviceFrom(deviceId, data)
                            } else null
                        } catch (_: Exception) {
                            null
                        }
                    }
                    trySend(devices)
                }
            }
        }
        awaitClose { registration.remove() }
    }

    override suspend fun removeDevice(deviceId: String): Result<Unit> {
        val user = authService.currentUser ?: return Result.failure(PairingError.NotSignedIn)
        return runCatching {
            val supervisorPath = FirestorePaths.supervisor(deviceId, user.id)
            firestore.document(supervisorPath).delete().await()
            Unit
        }
    }

    private fun convertTimestamps(rawData: Map<String, Any?>): Map<String, Any?> {
        return rawData.mapValues { (_, value) ->
            when (value) {
                is Timestamp -> value.toDate().time
                is Map<*, *> -> @Suppress("UNCHECKED_CAST") convertTimestamps(value as Map<String, Any?>)
                else -> value
            }
        }
    }
}

actual fun createClientPairingRepository(authService: AuthService): ClientPairingRepository =
    AndroidClientPairingRepository(authService)
