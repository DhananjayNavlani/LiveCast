package com.dhananjay.livecast.protocol

import kotlin.random.Random

object GroupFields {
    const val NAME = "name"
    const val OWNER_UID = "ownerUid"
    const val CREATED_AT = "createdAt"
    const val EMAIL = "email"
    const val ROLE = "role"
    const val ADDED_AT = "addedAt"
    const val GROUP_ID = "groupId"
    const val HOST_UID = "hostUid"
    const val DISPLAY_NAME = "displayName"
    const val MODEL = "model"
    const val ONLINE = "online"
    const val LAST_SEEN = "lastSeen"
    const val ENROLLED_AT = "enrolledAt"
}

// Mappers take maps whose timestamps the platform already converted to epoch-millis Long (normalizeTimestamps).
fun groupFrom(id: String, data: Map<String, Any?>): Group? {
    val name = data[GroupFields.NAME] as? String ?: return null
    val ownerUid = data[GroupFields.OWNER_UID] as? String ?: return null
    val createdAtEpochMs = (data[GroupFields.CREATED_AT] as? Number)?.toLong() ?: 0L
    return Group(
        id = id,
        name = name,
        ownerUid = ownerUid,
        createdAtEpochMs = createdAtEpochMs,
    )
}

fun groupMemberFrom(groupId: String, emailId: String, data: Map<String, Any?>): GroupMember? {
    val roleStr = data[GroupFields.ROLE] as? String ?: return null
    val role = GroupRules.roleFrom(roleStr) ?: return null
    val email = (data[GroupFields.EMAIL] as? String) ?: emailId
    val name = data[GroupFields.NAME] as? String
    val addedAtEpochMs = (data[GroupFields.ADDED_AT] as? Number)?.toLong() ?: 0L
    return GroupMember(
        groupId = groupId,
        email = email,
        role = role,
        name = name,
        addedAtEpochMs = addedAtEpochMs,
    )
}

fun hostDeviceFrom(id: String, data: Map<String, Any?>): HostDevice? {
    val displayName = data[GroupFields.DISPLAY_NAME] as? String ?: return null
    val model = data[GroupFields.MODEL] as? String ?: return null
    val online = data[GroupFields.ONLINE] as? Boolean ?: false
    val lastSeenEpochMs = (data[GroupFields.LAST_SEEN] as? Number)?.toLong() ?: 0L
    val groupId = data[GroupFields.GROUP_ID] as? String
    val enrolledAtEpochMs = (data[GroupFields.ENROLLED_AT] as? Number)?.toLong()
    return HostDevice(
        id = id,
        displayName = displayName,
        model = model,
        online = online,
        lastSeenEpochMs = lastSeenEpochMs,
        groupId = groupId,
        enrolledAtEpochMs = enrolledAtEpochMs,
    )
}

object GroupWrites {
    private const val ALPHANUMERIC = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

    fun newGroupId(random: Random = Random.Default): String {
        return (1..20)
            .map { ALPHANUMERIC[random.nextInt(ALPHANUMERIC.length)] }
            .joinToString("")
    }

    fun groupFields(name: String, ownerUid: String, createdAt: Any): Map<String, Any?> = mapOf(
        GroupFields.NAME to name,
        GroupFields.OWNER_UID to ownerUid,
        GroupFields.CREATED_AT to createdAt,
    )

    fun memberFields(email: String, role: GroupRole, name: String?, addedAt: Any): Map<String, Any?> = mapOf(
        GroupFields.EMAIL to GroupRules.normalizeEmail(email),
        GroupFields.ROLE to GroupRules.roleWire(role),
        GroupFields.NAME to name,
        GroupFields.ADDED_AT to addedAt,
    )

    fun enrollFields(device: HostDevice, groupId: String, now: Any): Map<String, Any?> = mapOf(
        GroupFields.GROUP_ID to groupId,
        GroupFields.HOST_UID to device.id,
        GroupFields.DISPLAY_NAME to device.displayName,
        GroupFields.MODEL to device.model,
        GroupFields.ONLINE to true,
        GroupFields.LAST_SEEN to now,
        GroupFields.ENROLLED_AT to now,
    )

    fun heartbeatFields(online: Boolean, now: Any): Map<String, Any?> = mapOf(
        GroupFields.ONLINE to online,
        GroupFields.LAST_SEEN to now,
    )

    fun removeFromGroupFields(): Map<String, Any?> = mapOf(
        GroupFields.GROUP_ID to null,
    )
}
