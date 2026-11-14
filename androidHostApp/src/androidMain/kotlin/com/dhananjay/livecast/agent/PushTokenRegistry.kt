package com.dhananjay.livecast.agent

import com.dhananjay.livecast.group.DeviceIdentity
import com.dhananjay.livecast.group.DeviceRegistry
import com.dhananjay.livecast.protocol.FirestorePaths
import com.dhananjay.livecast.protocol.GroupWrites
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
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

class FirebasePushTokenSource : PushTokenSource {
    override suspend fun currentToken(): String? = try {
        FirebaseMessaging.getInstance().token.await()
    } catch (_: Exception) {
        null
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
        val resolvedToken = token ?: source.currentToken() ?: return Result.success(Unit)
        return store.save(deviceId, resolvedToken)
    }

    override suspend fun onEnrolled(deviceId: String, groupId: String) {
        val token = source.currentToken() ?: return
        store.save(deviceId, token)
    }
}
