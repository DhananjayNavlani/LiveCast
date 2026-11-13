package com.dhananjay.livecast.protocol

object FirestorePaths {
    const val DEVICES = "devices"
    const val SUPERVISORS = "supervisors"
    const val PAIRING_CODES = "pairingCodes"
    const val PAIRING_REQUESTS = "pairingRequests"
    const val GROUPS = "groups"
    const val MEMBERS = "members"

    private val MEMBER_PATH_REGEX = Regex("""(?:^|/)groups/([^/]+)/members/([^/]+?)(?:/|$)""")

    fun device(deviceId: String): String = "$DEVICES/$deviceId"
    fun supervisor(deviceId: String, uid: String): String = "$DEVICES/$deviceId/$SUPERVISORS/$uid"
    fun pairingCode(code: String): String = "$PAIRING_CODES/$code"
    fun pairingRequest(id: String): String = "$PAIRING_REQUESTS/$id"
    fun group(groupId: String): String = "$GROUPS/$groupId"
    fun members(groupId: String): String = "$GROUPS/$groupId/$MEMBERS"
    fun member(groupId: String, email: String): String = "$GROUPS/$groupId/$MEMBERS/${GroupRules.normalizeEmail(email)}"
    fun groupIdFromMemberPath(path: String): String? =
        MEMBER_PATH_REGEX.findAll(path).lastOrNull()?.groupValues?.get(1)
}

object PairingFields {
    const val DEVICE_ID = "deviceId"
    const val HOST_UID = "hostUid"
    const val DISPLAY_NAME = "displayName"
    const val MODEL = "model"
    const val ONLINE = "online"
    const val LAST_SEEN = "lastSeen"
    const val LAST_SEEN_EPOCH_MS = "lastSeenEpochMs"
    const val CREATED_AT = "createdAt"
    const val EXPIRES_AT = "expiresAt"
    const val CODE = "code"
    const val CLIENT_UID = "clientUid"
    const val CLIENT_NAME = "clientName"
    const val CLIENT_EMAIL = "clientEmail"
    const val STATUS = "status"
    const val DECIDED_AT = "decidedAt"
    const val UID = "uid"
    const val NAME = "name"
    const val EMAIL = "email"
    const val REQUEST_ID = "requestId"
    const val ADDED_AT = "addedAt"
}

fun pairingCodeFrom(code: String, data: Map<String, Any?>): PairingCode? {
    val deviceId = data[PairingFields.DEVICE_ID] as? String ?: return null
    val hostUid = data[PairingFields.HOST_UID] as? String ?: return null
    val createdAtEpochMs = (data[PairingFields.CREATED_AT] as? Number)?.toLong() ?: return null
    val expiresAtEpochMs = (data[PairingFields.EXPIRES_AT] as? Number)?.toLong() ?: return null
    return PairingCode(
        code = code,
        deviceId = deviceId,
        hostUid = hostUid,
        createdAtEpochMs = createdAtEpochMs,
        expiresAtEpochMs = expiresAtEpochMs
    )
}

fun pairingRequestFrom(id: String, data: Map<String, Any?>): PairingRequest? {
    val code = data[PairingFields.CODE] as? String ?: return null
    val deviceId = data[PairingFields.DEVICE_ID] as? String ?: return null
    val clientUid = data[PairingFields.CLIENT_UID] as? String ?: return null
    val clientName = data[PairingFields.CLIENT_NAME] as? String ?: return null
    val clientEmail = data[PairingFields.CLIENT_EMAIL] as? String
    val statusStr = data[PairingFields.STATUS] as? String ?: return null
    val status = when (statusStr) {
        "pending" -> PairingStatus.PENDING
        "approved" -> PairingStatus.APPROVED
        "denied" -> PairingStatus.DENIED
        else -> return null
    }
    val createdAtEpochMs = (data[PairingFields.CREATED_AT] as? Number)?.toLong() ?: return null
    val decidedAtEpochMs = (data[PairingFields.DECIDED_AT] as? Number)?.toLong()

    return PairingRequest(
        id = id,
        code = code,
        deviceId = deviceId,
        clientUid = clientUid,
        clientName = clientName,
        clientEmail = clientEmail,
        status = status,
        createdAtEpochMs = createdAtEpochMs,
        decidedAtEpochMs = decidedAtEpochMs
    )
}

fun supervisorFrom(uid: String, data: Map<String, Any?>): Supervisor? {
    val name = data[PairingFields.NAME] as? String ?: return null
    val email = data[PairingFields.EMAIL] as? String
    val requestId = data[PairingFields.REQUEST_ID] as? String ?: return null
    val addedAtEpochMs = (data[PairingFields.ADDED_AT] as? Number)?.toLong() ?: return null

    return Supervisor(
        uid = uid,
        name = name,
        email = email,
        requestId = requestId,
        addedAtEpochMs = addedAtEpochMs
    )
}

fun PairingStatus.wire(): String = when (this) {
    PairingStatus.PENDING -> "pending"
    PairingStatus.APPROVED -> "approved"
    PairingStatus.DENIED -> "denied"
}
