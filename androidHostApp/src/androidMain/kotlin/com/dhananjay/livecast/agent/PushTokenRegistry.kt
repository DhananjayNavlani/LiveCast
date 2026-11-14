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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

interface EnrollmentState {
    suspend fun currentGroupId(): String?
}

class DeviceRegistryEnrollmentState(
    private val identity: DeviceIdentity,
    private val registry: DeviceRegistry
) : EnrollmentState {
    override suspend fun currentGroupId(): String? {
        val deviceId = identity.deviceId ?: return null
        return withTimeoutOrNull(10_000L) {
            try {
                registry.observeDevice(deviceId).first()?.groupId
            } catch (_: Exception) {
                null
            }
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
