package com.dhananjay.livecast.cast.data

import android.content.Context
import android.util.Log
import com.dhananjay.livecast.cast.data.model.DeviceInfo
import com.dhananjay.livecast.cast.data.model.DeviceOnline
import com.dhananjay.livecast.cast.data.model.LiveCastUser
import com.dhananjay.livecast.cast.data.repositories.AuthRepository
import com.dhananjay.livecast.cast.utils.Constants
import com.dhananjay.livecast.cast.utils.DeviceInfoHelper
import com.google.firebase.Timestamp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.toObject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.coroutineContext

class RemoteDataSource(
    private val context: Context,
    private val firestore: FirebaseFirestore,
    private val crashlytics: FirebaseCrashlytics,
    private val authRepository: AuthRepository
) {
    private val TAG = javaClass.simpleName
    private val userId get() = authRepository.getCurrentUser()?.uid

    companion object {
        // Consider a user offline if not seen for 2 minutes
        private const val ONLINE_TIMEOUT_SECONDS = 120L
        // Heartbeat interval - update last_seen every 30 seconds
        private const val HEARTBEAT_INTERVAL_MS = 30_000L
    }

    private var heartbeatJob: Job? = null
    private var cachedDeviceInfo: DeviceInfo? = null
    fun getConfigCollectionFlow() = callbackFlow<Result<DocumentSnapshot>> {
        val configCollectionRef = firestore.collection("config")
        val configDocumentRef = configCollectionRef.document("android")

        val listener = configDocumentRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(Result.failure(error))
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                trySend(Result.success(snapshot))
            } else {
                trySend(Result.failure(Exception("Document does not exist")))
            }
        }

        awaitClose {
            listener.remove()
            close()
        }
    }

    val devicesOnline: Flow<List<LiveCastUser>> = callbackFlow {
        val listener = firestore.collection("users")
            .whereEqualTo("is_online", true)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    Log.e(TAG, "Error fetching online users: $error")
                    return@addSnapshotListener
                }

                val now = Timestamp.now().seconds
                val users = snapshot.documents
                    .mapNotNull { it.toObject<LiveCastUser>() }
                    .filter { user ->
                        // Filter out stale users (last_seen older than timeout)
                        val lastSeenSeconds = user.lastSeen?.seconds ?: 0L
                        (now - lastSeenSeconds) < ONLINE_TIMEOUT_SECONDS
                    }
                trySend(users)
            }
        awaitClose { listener.remove() }
    }


    /**
     * Mark the current user as online and start heartbeat.
     * Uses atomic operations to prevent race conditions.
     */
    suspend fun setUserOnline(scope: kotlinx.coroutines.CoroutineScope): Boolean {
        return try {
            val id = userId ?: return false

            // Collect device info
            cachedDeviceInfo = DeviceInfoHelper.getDeviceInfo(context)
            val deviceInfo = cachedDeviceInfo!!

            // Atomic update - set online status with device info
            firestore.collection("users").document(id).set(
                mapOf(
                    "is_online" to true,
                    "is_broadcaster" to true,
                    "is_viewer" to false,
                    "platform" to "android",
                    "last_seen" to FieldValue.serverTimestamp(),
                    "width_pixels" to deviceInfo.screenWidth,
                    "height_pixels" to deviceInfo.screenHeight,
                    "device" to mapOf(
                        "device_id" to deviceInfo.deviceId,
                        "device_name" to deviceInfo.deviceName,
                        "manufacturer" to deviceInfo.manufacturer,
                        "model" to deviceInfo.model,
                        "brand" to deviceInfo.brand,
                        "sdk_version" to deviceInfo.sdkVersion,
                        "android_version" to deviceInfo.androidVersion,
                        "screen_width" to deviceInfo.screenWidth,
                        "screen_height" to deviceInfo.screenHeight,
                        "screen_density" to deviceInfo.screenDensity,
                        "battery_level" to deviceInfo.batteryLevel,
                        "is_charging" to deviceInfo.isCharging,
                        "network_type" to deviceInfo.networkType,
                        "ip_address" to deviceInfo.ipAddress,
                        "app_version" to deviceInfo.appVersion,
                        "app_version_code" to deviceInfo.appVersionCode,
                    )
                ),
                SetOptions.merge()
            ).await()

            // Start heartbeat to keep updating last_seen
            startHeartbeat(scope)

            // Update rooms/online count atomically
            updateOnlineRoomCount(increment = true)

            Log.d(TAG, "User $id is now online with device: ${deviceInfo.deviceName}")
            true
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            crashlytics.recordException(e)
            Log.e(TAG, "Error setting user online: $e")
            false
        }
    }

    /**
     * Mark the current user as offline and stop heartbeat.
     */
    suspend fun setUserOffline(): Boolean {
        return try {
            // Stop heartbeat first
            stopHeartbeat()

            val id = userId ?: return false

            // Atomic update - set offline status
            firestore.collection("users").document(id).set(
                mapOf(
                    "is_online" to false,
                    "last_seen" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            ).await()

            // Update rooms/online count atomically
            updateOnlineRoomCount(increment = false)

            Log.d(TAG, "User $id is now offline")
            true
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            crashlytics.recordException(e)
            Log.e(TAG, "Error setting user offline: $e")
            false
        }
    }

    /**
     * Update last_seen timestamp and dynamic device info (called by heartbeat)
     */
    private suspend fun updateLastSeen(): Boolean {
        return try {
            val id = userId ?: return false

            // Get updated device info for dynamic fields
            val deviceInfo = DeviceInfoHelper.getDeviceInfo(context)

            firestore.collection("users").document(id).update(
                mapOf(
                    "last_seen" to FieldValue.serverTimestamp(),
                    "device.battery_level" to deviceInfo.batteryLevel,
                    "device.is_charging" to deviceInfo.isCharging,
                    "device.network_type" to deviceInfo.networkType,
                    "device.ip_address" to deviceInfo.ipAddress,
                )
            ).await()

            Log.d(TAG, "Updated last_seen for user $id (battery: ${deviceInfo.batteryLevel}%)")
            true
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            Log.e(TAG, "Error updating last_seen: $e")
            false
        }
    }

    /**
     * Start periodic heartbeat to update last_seen
     */
    private fun startHeartbeat(scope: kotlinx.coroutines.CoroutineScope) {
        stopHeartbeat() // Cancel any existing heartbeat

        heartbeatJob = scope.launch {
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MS)
                updateLastSeen()
            }
        }
        Log.d(TAG, "Heartbeat started")
    }

    /**
     * Stop the heartbeat job
     */
    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        Log.d(TAG, "Heartbeat stopped")
    }

    /**
     * Atomically update the online room count
     */
    private suspend fun updateOnlineRoomCount(increment: Boolean) {
        try {
            val roomDoc = firestore.collection("rooms").document("online")

            if (increment) {
                roomDoc.set(
                    mapOf(
                        "count" to FieldValue.increment(1),
                        "devices" to FieldValue.arrayUnion(Constants.DEVICE_ID),
                        "names" to FieldValue.arrayUnion(Constants.DEVICE_NAME),
                        "last_updated" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                ).await()
            } else {
                roomDoc.set(
                    mapOf(
                        "count" to FieldValue.increment(-1),
                        "devices" to FieldValue.arrayRemove(Constants.DEVICE_ID),
                        "names" to FieldValue.arrayRemove(Constants.DEVICE_NAME),
                        "last_updated" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                ).await()
            }
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            Log.e(TAG, "Error updating online room count: $e")
        }
    }

    // Legacy methods - kept for backward compatibility, delegate to new methods
    @Deprecated("Use setUserOnline instead", ReplaceWith("setUserOnline(scope)"))
    suspend fun addDeviceOnline(): Boolean {
        return try {
            firestore.collection("rooms").document("online").let {
                val deviceOnline = it.get().await().takeIf { it.exists() }?.toObject<DeviceOnline>()
                val hasDevice = deviceOnline?.devices?.contains(Constants.DEVICE_ID) ?: false

                Log.d(TAG, "addDeviceOnline: The collection has device ? $deviceOnline")
                if (!hasDevice) {
                    var count = deviceOnline?.count?.coerceAtLeast(0) ?: 0
                    it.set(
                        hashMapOf(
                            "count" to ++count,
                            "names" to FieldValue.arrayUnion(Constants.DEVICE_NAME),
                            "devices" to FieldValue.arrayUnion(Constants.DEVICE_ID)
                        ),
                        SetOptions.merge()
                    )
                }
                userId?.let { id ->
                    firestore.collection("users").document(id).set(
                        mapOf(
                            "is_online" to true,
                            "last_seen" to FieldValue.serverTimestamp(),
                            "device_id" to Constants.DEVICE_ID,
                            "device_name" to Constants.DEVICE_NAME
                        ),
                        SetOptions.merge()
                    ).await()
                }

            }

            true
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            Log.e(TAG, "Error adding device online: $e")
            false
        }

    }

    @Deprecated("Use setUserOffline instead", ReplaceWith("setUserOffline()"))
    suspend fun removeDeviceOnline(): Boolean {
        return try {
            firestore.collection("rooms").document("online").let {
                val deviceOnline = it.get().await().takeIf { it.exists() }?.toObject<DeviceOnline>()
                val hasDevice = deviceOnline?.devices?.contains(Constants.DEVICE_ID) ?: false

                Log.d(TAG, "removeDeviceOnline: The collection has device ? $deviceOnline")
                if (hasDevice) {
                    var count = deviceOnline?.count?.coerceAtLeast(1) ?: 1
                    it.set(
                        hashMapOf(
                            "count" to --count,
                            "names" to FieldValue.arrayRemove(Constants.DEVICE_NAME),
                            "devices" to FieldValue.arrayRemove(Constants.DEVICE_ID)
                        ),
                        SetOptions.merge()
                    )
                    userId?.let { id ->
                        firestore.collection("users").document(id).set(
                            mapOf(
                                "is_online" to false,
                                "last_seen" to FieldValue.serverTimestamp()
                            ),
                            SetOptions.merge()
                        ).await()
                    }
                }
            }
            true
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            Log.e(TAG, "Error removing device online: $e")
            false
        }

    }
    suspend fun getUser(uid: String): Result<DocumentSnapshot> {
        val docRef = firestore.collection("users").document(uid)
        return try {
            Result.success(docRef.get().await())
        }catch (e: Exception){
            crashlytics.recordException(e)
            Result.failure(e)
        }
    }

    suspend fun userExists(uid: String): Boolean {
        val docRef = firestore.collection("users").document(uid)
        return try {
            val snapshot = docRef.get().await()
            snapshot.exists()
        }catch (e: Exception){
            crashlytics.recordException(e)
            false
        }
    }

    suspend fun addUser(user:LiveCastUser){
        val docRef = firestore.collection("users").document(user.uid)
        try {
            docRef.set(user, SetOptions.merge()).await()
        }catch (e: Exception){
            crashlytics.recordException(e)
        }
    }

}