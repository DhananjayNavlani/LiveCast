package com.dhananjay.livecast.client.pairing

import com.dhananjay.livecast.auth.AuthService
import com.dhananjay.livecast.firestore.grpc.FirestoreConnectionConfig
import com.dhananjay.livecast.firestore.grpc.FirestoreDoc
import com.dhananjay.livecast.firestore.grpc.FirestoreGrpcClient
import com.dhananjay.livecast.firestore.grpc.FirestoreQuery
import com.dhananjay.livecast.firestore.grpc.FirestoreWrite
import com.dhananjay.livecast.protocol.FirestorePaths
import com.dhananjay.livecast.protocol.HostDevice
import com.dhananjay.livecast.protocol.PairingFields
import com.dhananjay.livecast.protocol.PairingRequest
import com.dhananjay.livecast.protocol.PairingStatus
import com.dhananjay.livecast.protocol.hostDeviceFrom
import com.dhananjay.livecast.protocol.pairingCodeFrom
import com.dhananjay.livecast.protocol.pairingRequestFrom
import com.dhananjay.livecast.protocol.wire
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.io.File
import java.time.Instant
import java.util.Properties

class DesktopClientPairingRepository(
    private val authService: AuthService,
    configDir: File = File(System.getProperty("user.home"), ".livecast")
) : ClientPairingRepository, AutoCloseable {

    private val projectId: String? = loadProjectId(configDir)

    private val missingConfigError = if (projectId == null) {
        PairingError.Unsupported(
            "Missing FIREBASE_PROJECT_ID environment variable or firebase_project_id in ~/.livecast/config.properties"
        )
    } else null

    private val grpcClient: FirestoreGrpcClient? = projectId?.let { pid ->
        val emulatorHost = System.getenv("FIRESTORE_EMULATOR_HOST")
        val (host, port, plaintext) = if (!emulatorHost.isNullOrBlank()) {
            val parts = emulatorHost.split(":")
            Triple(parts[0], parts.getOrNull(1)?.toIntOrNull() ?: 8080, true)
        } else {
            Triple("firestore.googleapis.com", 443, false)
        }
        val config = FirestoreConnectionConfig(
            projectId = pid,
            host = host,
            port = port,
            plaintext = plaintext
        )
        FirestoreGrpcClient(
            config = config,
            tokenProvider = { forceRefresh -> authService.getIdToken(forceRefresh) }
        )
    }

    override suspend fun submitCode(code: String): Result<SubmittedRequest> {
        val client = grpcClient ?: return Result.failure(missingConfigError!!)
        val user = authService.currentUser ?: return Result.failure(PairingError.NotSignedIn)
        if (user.isAnonymous) return Result.failure(PairingError.AnonymousNotAllowed)

        return runCatching {
            val codePath = FirestorePaths.pairingCode(code)
            val codeDoc = client.get(codePath) ?: throw PairingError.InvalidOrExpiredCode
            val codeData = convertInstants(codeDoc.fields)
            val pairingCode = pairingCodeFrom(code, codeData) ?: throw PairingError.InvalidOrExpiredCode

            val nowMs = System.currentTimeMillis()
            if (nowMs >= pairingCode.expiresAtEpochMs) {
                throw PairingError.InvalidOrExpiredCode
            }

            val requestFields = mapOf(
                PairingFields.CODE to code,
                PairingFields.DEVICE_ID to pairingCode.deviceId,
                PairingFields.CLIENT_UID to user.id,
                PairingFields.CLIENT_NAME to (user.displayName ?: user.email ?: "Supervisor"),
                PairingFields.CLIENT_EMAIL to user.email,
                PairingFields.STATUS to PairingStatus.PENDING.wire(),
                PairingFields.CREATED_AT to Instant.now()
            )

            val requestId = client.createWithAutoId(FirestorePaths.PAIRING_REQUESTS, requestFields)
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
        val client = grpcClient ?: return flowOf(null)
        return client.listenDocument(FirestorePaths.pairingRequest(requestId))
            .map { doc ->
                if (doc != null) {
                    pairingRequestFrom(doc.id, convertInstants(doc.fields))
                } else null
            }
    }

    override fun observeMyDevices(): Flow<List<HostDevice>> {
        val client = grpcClient ?: return flowOf(emptyList())
        val user = authService.currentUser ?: return flowOf(emptyList())

        val query = FirestoreQuery(
            parentPath = "",
            collectionId = FirestorePaths.SUPERVISORS,
            allDescendants = true,
            whereEquals = listOf(PairingFields.UID to user.id)
        )

        return client.listenQuery(query).map { supervisorDocs ->
            val deviceIds = supervisorDocs.mapNotNull { doc ->
                val parts = doc.path.split("/")
                val devIdx = parts.indexOf(FirestorePaths.DEVICES)
                if (devIdx != -1 && devIdx + 1 < parts.size) {
                    parts[devIdx + 1]
                } else null
            }.distinct()

            deviceIds.mapNotNull { deviceId ->
                try {
                    val devDoc = client.get(FirestorePaths.device(deviceId))
                    if (devDoc != null) {
                        hostDeviceFrom(deviceId, convertInstants(devDoc.fields))
                    } else null
                } catch (_: Exception) {
                    null
                }
            }
        }
    }

    override suspend fun removeDevice(deviceId: String): Result<Unit> {
        val client = grpcClient ?: return Result.failure(missingConfigError!!)
        val user = authService.currentUser ?: return Result.failure(PairingError.NotSignedIn)

        return runCatching {
            val supervisorPath = FirestorePaths.supervisor(deviceId, user.id)
            client.commit(listOf(FirestoreWrite.Delete(supervisorPath)))
        }
    }

    override fun close() {
        grpcClient?.close()
    }

    private fun convertInstants(fields: Map<String, Any?>): Map<String, Any?> {
        return fields.mapValues { (_, value) ->
            when (value) {
                is Instant -> value.toEpochMilli()
                is Map<*, *> -> @Suppress("UNCHECKED_CAST") convertInstants(value as Map<String, Any?>)
                else -> value
            }
        }
    }

    companion object {
        private fun loadProjectId(configDir: File): String? {
            System.getenv("FIREBASE_PROJECT_ID")?.takeIf { it.isNotBlank() }?.let { return it }
            val configFile = File(configDir, "config.properties")
            return try {
                if (configFile.exists()) {
                    val props = Properties().apply {
                        configFile.inputStream().use { load(it) }
                    }
                    props.getProperty("firebase_project_id")?.takeIf { it.isNotBlank() }
                } else null
            } catch (_: Exception) {
                null
            }
        }
    }
}

actual fun createClientPairingRepository(authService: AuthService): ClientPairingRepository =
    DesktopClientPairingRepository(authService)
