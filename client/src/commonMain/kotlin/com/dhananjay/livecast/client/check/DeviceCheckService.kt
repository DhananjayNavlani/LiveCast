package com.dhananjay.livecast.client.check

sealed interface CheckRequestResult {
    data class Requested(val checkId: String) : CheckRequestResult
    data object AppRemoved : CheckRequestResult
    data object NoPushToken : CheckRequestResult
    data object NotAllowed : CheckRequestResult
    data object RateLimited : CheckRequestResult
    data class Failed(val message: String) : CheckRequestResult
}

interface DeviceCheckService {
    suspend fun requestCheck(deviceId: String): CheckRequestResult
}

sealed interface CheckState {
    data object Idle : CheckState
    data object Checking : CheckState
    data class Online(val atEpochMs: Long) : CheckState
    data object Offline : CheckState
    data object AppRemoved : CheckState
    data object NotSetUp : CheckState
    data object RateLimited : CheckState
    data class Error(val message: String) : CheckState
}
