package com.dhananjay.livecast.client.pairing

import com.dhananjay.livecast.auth.SessionProvider
import com.dhananjay.livecast.protocol.FirestorePaths
import com.dhananjay.livecast.protocol.HostDevice
import com.dhananjay.livecast.protocol.PairingFields
import com.dhananjay.livecast.protocol.PairingRequest
import com.dhananjay.livecast.protocol.hostDeviceFrom
import com.dhananjay.livecast.protocol.normalizeTimestamps
import com.dhananjay.livecast.protocol.pairingCodeFrom
import com.dhananjay.livecast.protocol.pairingRequestFrom
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.koin.core.module.Module
import org.koin.dsl.module

private val toMillis: (Any) -> Long? = { (it as? Timestamp)?.toDate()?.time }

class FirestoreSdkPairingRequestRepository(
    private val firestore: FirebaseFirestore,
    private val session: SessionProvider
) : PairingRequestRepository {

    override suspend fun submitCode(code: String): Result<SubmittedRequest> {
        val user = session.currentUser ?: return Result.failure(PairingError.NotSignedIn)
        if (user.isAnonymous) return Result.failure(PairingError.AnonymousNotAllowed)

        return runCatching {
            val codeSnap = firestore.document(FirestorePaths.pairingCode(code)).get().await()
            if (!codeSnap.exists()) {
                throw PairingError.InvalidOrExpiredCode
            }

            val rawData = codeSnap.data ?: emptyMap()
            val codeData = normalizeTimestamps(rawData, toMillis)
            val pairingCode = pairingCodeFrom(code, codeData) ?: throw PairingError.InvalidOrExpiredCode

            val nowMs = System.currentTimeMillis()
            PairingRequestPolicy.validateCode(pairingCode, nowMs).getOrThrow()

            val baseFields = PairingRequestPolicy.requestFields(user, pairingCode)
            val requestData = HashMap<String, Any?>(baseFields).apply {
                put(PairingFields.CREATED_AT, Timestamp.now())
            }

            val ref = firestore.collection(FirestorePaths.PAIRING_REQUESTS).add(requestData).await()
            val request = PairingRequestPolicy.pendingRequest(
                id = ref.id,
                user = user,
                code = pairingCode,
                nowEpochMs = nowMs
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
                val rawData = snapshot.data ?: emptyMap()
                val data = normalizeTimestamps(rawData, toMillis)
                trySend(pairingRequestFrom(snapshot.id, data))
            } else {
                trySend(null)
            }
        }
        awaitClose { registration.remove() }
    }
}

class FirestoreSdkSupervisedDeviceRepository(
    private val firestore: FirebaseFirestore,
    private val session: SessionProvider
) : SupervisedDeviceRepository {

    override fun observeMyDevices(): Flow<List<HostDevice>> = callbackFlow {
        val user = session.currentUser
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
                        PairingRequestPolicy.deviceIdFromSupervisorPath(doc.reference.path)
                    }.distinct()

                    val devices = deviceIds.mapNotNull { deviceId ->
                        try {
                            val devSnap = firestore.document(FirestorePaths.device(deviceId)).get().await()
                            if (devSnap.exists()) {
                                val rawData = devSnap.data ?: emptyMap()
                                val data = normalizeTimestamps(rawData, toMillis)
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
        val user = session.currentUser ?: return Result.failure(PairingError.NotSignedIn)
        return runCatching {
            val supervisorPath = FirestorePaths.supervisor(deviceId, user.id)
            firestore.document(supervisorPath).delete().await()
            Unit
        }
    }
}

actual val pairingPlatformModule: Module = module {
    single { FirebaseFirestore.getInstance() }
    single<PairingRequestRepository> { FirestoreSdkPairingRequestRepository(get(), get()) }
    single<SupervisedDeviceRepository> { FirestoreSdkSupervisedDeviceRepository(get(), get()) }
}
