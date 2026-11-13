package com.dhananjay.livecast.client.pairing

import com.dhananjay.livecast.auth.User
import com.dhananjay.livecast.protocol.FirestorePaths
import com.dhananjay.livecast.protocol.PairingCode
import com.dhananjay.livecast.protocol.PairingFields
import com.dhananjay.livecast.protocol.PairingRequest
import com.dhananjay.livecast.protocol.PairingStatus
import com.dhananjay.livecast.protocol.wire

object PairingRequestPolicy {

    private fun clientName(user: User): String = user.displayName ?: user.email ?: "Supervisor"

    fun validateCode(code: PairingCode?, nowEpochMs: Long): Result<PairingCode> {
        if (code == null) return Result.failure(PairingError.InvalidOrExpiredCode)
        if (nowEpochMs >= code.expiresAtEpochMs) return Result.failure(PairingError.InvalidOrExpiredCode)
        return Result.success(code)
    }

    fun requestFields(user: User, code: PairingCode): Map<String, Any?> {
        return mapOf(
            PairingFields.CODE to code.code,
            PairingFields.DEVICE_ID to code.deviceId,
            PairingFields.CLIENT_UID to user.id,
            PairingFields.CLIENT_NAME to clientName(user),
            PairingFields.CLIENT_EMAIL to user.email,
            PairingFields.STATUS to PairingStatus.PENDING.wire()
        )
    }

    fun pendingRequest(
        id: String,
        user: User,
        code: PairingCode,
        nowEpochMs: Long
    ): PairingRequest {
        return PairingRequest(
            id = id,
            code = code.code,
            deviceId = code.deviceId,
            clientUid = user.id,
            clientName = clientName(user),
            clientEmail = user.email,
            status = PairingStatus.PENDING,
            createdAtEpochMs = nowEpochMs,
            decidedAtEpochMs = null
        )
    }

    fun deviceIdFromSupervisorPath(path: String): String? {
        val segments = path.split("/").filter { it.isNotEmpty() }
        val devIdx = segments.indexOf(FirestorePaths.DEVICES)
        if (devIdx != -1 && devIdx + 2 < segments.size) {
            if (segments[devIdx + 2] == FirestorePaths.SUPERVISORS) {
                val deviceId = segments[devIdx + 1]
                if (deviceId.isNotEmpty()) {
                    return deviceId
                }
            }
        }
        return null
    }
}
