package com.dhananjay.livecast.pairing

import com.dhananjay.livecast.protocol.FirestorePaths
import com.dhananjay.livecast.protocol.PairingCode
import com.dhananjay.livecast.protocol.PairingFields
import com.dhananjay.livecast.protocol.PairingRules
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.tasks.await

interface PairingCodeIssuer {
    suspend fun createCode(deviceId: String): Result<PairingCode>
}

class FirestorePairingCodeIssuer(
    private val firestore: FirebaseFirestore,
    private val generator: PairingCodeGenerator,
    private val clock: () -> Long
) : PairingCodeIssuer {

    override suspend fun createCode(deviceId: String): Result<PairingCode> = runCatching {
        var lastError: Exception? = null
        for (attempt in 0..PairingRules.MAX_CODE_RETRIES) {
            val code = generator.next()
            val nowMs = clock()
            val nowTs = Timestamp(nowMs / 1000, ((nowMs % 1000) * 1_000_000).toInt())
            val expiresAtMs = nowMs + PairingRules.CODE_TTL_MS
            val expiresAtTs = Timestamp(expiresAtMs / 1000, ((expiresAtMs % 1000) * 1_000_000).toInt())
            val docRef = firestore.document(FirestorePaths.pairingCode(code))

            try {
                firestore.runTransaction { tx ->
                    val snapshot = tx.get(docRef)
                    if (snapshot.exists()) {
                        throw FirebaseFirestoreException(
                            "Pairing code already exists",
                            FirebaseFirestoreException.Code.ALREADY_EXISTS
                        )
                    }
                    val data = hashMapOf(
                        PairingFields.CODE to code,
                        PairingFields.DEVICE_ID to deviceId,
                        PairingFields.HOST_UID to deviceId,
                        PairingFields.CREATED_AT to nowTs,
                        PairingFields.EXPIRES_AT to expiresAtTs,
                    )
                    tx.set(docRef, data)
                }.await()

                return@runCatching PairingCode(
                    code = code,
                    deviceId = deviceId,
                    hostUid = deviceId,
                    createdAtEpochMs = nowTs.toDate().time,
                    expiresAtEpochMs = expiresAtTs.toDate().time,
                )
            } catch (e: Exception) {
                if (isAlreadyExistsException(e)) {
                    lastError = e
                } else {
                    throw e
                }
            }
        }
        throw lastError ?: IllegalStateException("Exceeded maximum retries generating pairing code")
    }
}

internal fun isAlreadyExistsException(e: Throwable): Boolean {
    var current: Throwable? = e
    while (current != null) {
        if (current is FirebaseFirestoreException && current.code == FirebaseFirestoreException.Code.ALREADY_EXISTS) {
            return true
        }
        current = current.cause
    }
    return false
}
