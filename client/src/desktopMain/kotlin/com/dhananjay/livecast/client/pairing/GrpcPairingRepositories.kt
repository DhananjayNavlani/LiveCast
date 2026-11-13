package com.dhananjay.livecast.client.pairing

import com.dhananjay.livecast.auth.IdTokenProvider
import com.dhananjay.livecast.auth.SessionProvider
import com.dhananjay.livecast.firestore.grpc.DocumentStore
import com.dhananjay.livecast.firestore.grpc.EnvPropertiesFirestoreConfigSource
import com.dhananjay.livecast.firestore.grpc.FirestoreConfigSource
import com.dhananjay.livecast.firestore.grpc.FirestoreConnectionConfig
import com.dhananjay.livecast.firestore.grpc.FirestoreGrpcClient
import com.dhananjay.livecast.firestore.grpc.FirestoreQuery
import com.dhananjay.livecast.firestore.grpc.FirestoreWrite
import com.dhananjay.livecast.protocol.FirestorePaths
import com.dhananjay.livecast.protocol.HostDevice
import com.dhananjay.livecast.protocol.PairingFields
import com.dhananjay.livecast.protocol.PairingRequest
import com.dhananjay.livecast.protocol.PairingStatus
import com.dhananjay.livecast.protocol.hostDeviceFrom
import com.dhananjay.livecast.protocol.normalizeTimestamps
import com.dhananjay.livecast.protocol.pairingCodeFrom
import com.dhananjay.livecast.protocol.pairingRequestFrom
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.koin.core.module.Module
import org.koin.dsl.module
import java.io.File
import java.time.Instant

class GrpcPairingRequestRepository(
    private val store: DocumentStore,
    private val session: SessionProvider
) : PairingRequestRepository {

    override suspend fun submitCode(code: String): Result<SubmittedRequest> {
        val user = session.currentUser ?: return Result.failure(PairingError.NotSignedIn)
        if (user.isAnonymous) return Result.failure(PairingError.AnonymousNotAllowed)

        return runCatching {
            val codePath = FirestorePaths.pairingCode(code)
            val codeDoc = store.get(codePath) ?: throw PairingError.InvalidOrExpiredCode
            val codeData = normalizeTimestamps(codeDoc.fields) { (it as? Instant)?.toEpochMilli() }
            val pairingCode = pairingCodeFrom(code, codeData) ?: throw PairingError.InvalidOrExpiredCode

            val nowMs = System.currentTimeMillis()
            PairingRequestPolicy.validateCode(pairingCode, nowMs).getOrThrow()

            val baseFields = PairingRequestPolicy.requestFields(user, pairingCode)
            val requestFields = HashMap<String, Any?>(baseFields).apply {
                put(PairingFields.CREATED_AT, Instant.now())
            }

            val requestId = store.createWithAutoId(FirestorePaths.PAIRING_REQUESTS, requestFields)
            val request = PairingRequest(
                id = requestId,
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

    override fun observeRequest(requestId: String): Flow<PairingRequest?> {
        return store.listenDocument(FirestorePaths.pairingRequest(requestId))
            .map { doc ->
                if (doc != null) {
                    val data = normalizeTimestamps(doc.fields) { (it as? Instant)?.toEpochMilli() }
                    pairingRequestFrom(doc.id, data)
                } else null
            }
    }
}

class GrpcSupervisedDeviceRepository(
    private val store: DocumentStore,
    private val session: SessionProvider
) : SupervisedDeviceRepository {

    override fun observeMyDevices(): Flow<List<HostDevice>> {
        val user = session.currentUser ?: return flowOf(emptyList())

        val query = FirestoreQuery(
            parentPath = "",
            collectionId = FirestorePaths.SUPERVISORS,
            allDescendants = true,
            whereEquals = listOf(PairingFields.UID to user.id)
        )

        return store.listenQuery(query).map { supervisorDocs ->
            val deviceIds = supervisorDocs.mapNotNull { doc ->
                PairingRequestPolicy.deviceIdFromSupervisorPath(doc.path)
            }.distinct()

            deviceIds.mapNotNull { deviceId ->
                try {
                    val devDoc = store.get(FirestorePaths.device(deviceId))
                    if (devDoc != null) {
                        val data = normalizeTimestamps(devDoc.fields) { (it as? Instant)?.toEpochMilli() }
                        hostDeviceFrom(deviceId, data)
                    } else null
                } catch (_: Exception) {
                    null
                }
            }
        }
    }

    override suspend fun removeDevice(deviceId: String): Result<Unit> {
        val user = session.currentUser ?: return Result.failure(PairingError.NotSignedIn)
        return runCatching {
            val supervisorPath = FirestorePaths.supervisor(deviceId, user.id)
            store.commit(listOf(FirestoreWrite.Delete(supervisorPath)))
        }
    }
}

private const val MISSING_CONFIG_MSG =
    "Missing FIREBASE_PROJECT_ID environment variable or firebase_project_id in ~/.livecast/config.properties"

actual val pairingPlatformModule: Module = module {
    val configSource = EnvPropertiesFirestoreConfigSource(File(System.getProperty("user.home"), ".livecast"))
    single<FirestoreConfigSource> { configSource }
    val config = configSource.load()
    if (config != null) {
        single<FirestoreConnectionConfig> { config }
        single<DocumentStore> {
            val idTokenProvider = get<IdTokenProvider>()
            FirestoreGrpcClient(
                config = get(),
                tokenProvider = { forceRefresh -> idTokenProvider.getIdToken(forceRefresh) }
            )
        }
        single<PairingRequestRepository> {
            GrpcPairingRequestRepository(get(), get())
        }
        single<SupervisedDeviceRepository> {
            GrpcSupervisedDeviceRepository(get(), get())
        }
    } else {
        single<PairingRequestRepository> {
            UnsupportedPairingRequestRepository(MISSING_CONFIG_MSG)
        }
        single<SupervisedDeviceRepository> {
            UnsupportedSupervisedDeviceRepository(MISSING_CONFIG_MSG)
        }
    }
}
