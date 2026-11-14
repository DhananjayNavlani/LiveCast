package com.dhananjay.livecast.agent

import android.util.Log
import com.dhananjay.livecast.group.DeviceIdentity
import com.dhananjay.livecast.group.DeviceRegistry
import com.dhananjay.livecast.protocol.FirestorePaths
import com.dhananjay.livecast.protocol.GroupWrites
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

sealed interface EnrollmentStatus {
    data class Enrolled(val groupId: String) : EnrollmentStatus
    data object NotEnrolled : EnrollmentStatus
    data object Unknown : EnrollmentStatus
}

interface EnrollmentState {
    suspend fun status(): EnrollmentStatus
}

suspend fun EnrollmentState.currentGroupId(): String? = when (val s = status()) {
    is EnrollmentStatus.Enrolled -> s.groupId
    EnrollmentStatus.NotEnrolled, EnrollmentStatus.Unknown -> null
}

suspend fun EnrollmentState.awaitStatus(
    retryDelaysMs: List<Long> = listOf(15_000L, 30_000L, 60_000L, 120_000L, 240_000L)
): EnrollmentStatus {
    var s = status()
    if (s !is EnrollmentStatus.Unknown) return s
    for (delayMs in retryDelaysMs) {
        delay(delayMs)
        s = status()
        if (s !is EnrollmentStatus.Unknown) return s
    }
    return EnrollmentStatus.Unknown
}

class DeviceRegistryEnrollmentState(
    private val identity: DeviceIdentity,
    private val registry: DeviceRegistry
) : EnrollmentState {
    override suspend fun status(): EnrollmentStatus {
        val deviceId = identity.deviceId ?: return EnrollmentStatus.NotEnrolled
        return try {
            withTimeoutOrNull(10_000L) {
                val device = registry.observeDevice(deviceId).first()
                val groupId = device?.groupId
                if (groupId != null) {
                    EnrollmentStatus.Enrolled(groupId)
                } else {
                    EnrollmentStatus.NotEnrolled
                }
            } ?: EnrollmentStatus.Unknown
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            EnrollmentStatus.Unknown
        }
    }
}

interface PushTokenStore {
    suspend fun save(deviceId: String, token: String): Result<Unit>
}

class FirestorePushTokenStore(
    private val firestore: FirebaseFirestore
) : PushTokenStore {
    override suspend fun save(deviceId: String, token: String): Result<Unit> = runCatching {
        val docRef = firestore.document(FirestorePaths.privatePush(deviceId))
        val fields = GroupWrites.pushTokenFields(token, FieldValue.serverTimestamp())
        docRef.set(fields, SetOptions.merge()).await()
    }
}

interface PushTokenSource {
    suspend fun currentToken(): String?
}

class FirebasePushTokenSource(
    private val messaging: FirebaseMessaging
) : PushTokenSource {
    override suspend fun currentToken(): String? = withTimeoutOrNull(10_000L) {
        try {
            messaging.token.await()
        } catch (_: Exception) {
            null
        }
    }
}

class PushTokenRegistry(
    private val identity: DeviceIdentity,
    private val enrollment: EnrollmentState,
    private val store: PushTokenStore,
    private val source: PushTokenSource
) : EnrollmentListener {

    suspend fun register(token: String? = null): Result<Unit> {
        val deviceId = identity.deviceId ?: return Result.success(Unit)
        val groupId = enrollment.currentGroupId() ?: return Result.success(Unit)
        val resolvedToken = token ?: withTimeoutOrNull(10_000L) {
            source.currentToken()
        }
        if (resolvedToken == null) {
            Log.w("PushTokenRegistry", "Push token unavailable for device $deviceId")
            return Result.failure(IllegalStateException("Push token unavailable for device $deviceId"))
        }
        val result = withTimeoutOrNull(10_000L) {
            store.save(deviceId, resolvedToken)
        } ?: Result.failure(TimeoutException("Timed out saving push token for device $deviceId"))

        result.onFailure { t ->
            Log.w("PushTokenRegistry", "Failed to save push token for device $deviceId", t)
        }
        return result
    }

    override suspend fun onEnrolled(deviceId: String, groupId: String) {
        val token = withTimeoutOrNull(10_000L) {
            source.currentToken()
        }
        if (token == null) {
            Log.w("PushTokenRegistry", "Push token unavailable on enrollment for device $deviceId")
            return
        }
        val result = withTimeoutOrNull(10_000L) {
            store.save(deviceId, token)
        } ?: Result.failure(TimeoutException("Timed out saving push token on enrollment for device $deviceId"))

        result.onFailure { t ->
            Log.w("PushTokenRegistry", "Failed to save push token on enrollment for device $deviceId", t)
        }
    }
}
