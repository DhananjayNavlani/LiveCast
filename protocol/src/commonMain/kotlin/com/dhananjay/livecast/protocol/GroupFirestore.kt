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
    const val ENROLLED_AT = "enrolledAt"
    const val ACCESSIBILITY = "accessibility"
    const val NOTIFICATIONS = "notifications"
    const val STATUS_AT = "statusAt"
    const val LAST_CHECK_ID = "lastCheckId"
    const val LAST_CHECK_AT = "lastCheckAt"
    const val FCM_TOKEN = "fcmToken"
    const val UPDATED_AT = "updatedAt"
    const val TYPE = "type"
    const val PACKAGE_NAME = "packageName"
    const val LABEL = "label"
    const val AT = "at"
    const val DETECTED_LATE = "detectedLate"
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
    val groupId = data[GroupFields.GROUP_ID] as? String
    val enrolledAtEpochMs = (data[GroupFields.ENROLLED_AT] as? Number)?.toLong()
    val accessibility = ToggleState.fromWire(data[GroupFields.ACCESSIBILITY])
    val notifications = ToggleState.fromWire(data[GroupFields.NOTIFICATIONS])
    val statusAtEpochMs = (data[GroupFields.STATUS_AT] as? Number)?.toLong()
    val lastCheckId = data[GroupFields.LAST_CHECK_ID] as? String
    val lastCheckAtEpochMs = (data[GroupFields.LAST_CHECK_AT] as? Number)?.toLong()
    return HostDevice(
        id = id,
        displayName = displayName,
        model = model,
        groupId = groupId,
        enrolledAtEpochMs = enrolledAtEpochMs,
        accessibility = accessibility,
        notifications = notifications,
        statusAtEpochMs = statusAtEpochMs,
        lastCheckId = lastCheckId,
        lastCheckAtEpochMs = lastCheckAtEpochMs,
    )
}

fun appEventFrom(id: String, data: Map<String, Any?>): AppEvent? {
    val type = AppEventType.fromWire(data[GroupFields.TYPE]) ?: return null
    val packageName = data[GroupFields.PACKAGE_NAME] as? String ?: return null
    val label = data[GroupFields.LABEL] as? String ?: ""
    val atEpochMs = (data[GroupFields.AT] as? Number)?.toLong() ?: return null
    val detectedLate = data[GroupFields.DETECTED_LATE] as? Boolean ?: false
    val groupId = data[GroupFields.GROUP_ID] as? String ?: return null
    return AppEvent(
        id = id,
        type = type,
        packageName = packageName,
        label = label,
        atEpochMs = atEpochMs,
        detectedLate = detectedLate,
        groupId = groupId,
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
        GroupFields.ENROLLED_AT to now,
    )

    fun statusFields(accessibilityOn: Boolean, notificationsOn: Boolean, now: Any): Map<String, Any?> = mapOf(
        GroupFields.ACCESSIBILITY to if (accessibilityOn) "on" else "off",
        GroupFields.NOTIFICATIONS to if (notificationsOn) "on" else "off",
        GroupFields.STATUS_AT to now,
    )

    fun checkReplyFields(checkId: String, accessibilityOn: Boolean, notificationsOn: Boolean, now: Any): Map<String, Any?> = mapOf(
        GroupFields.ACCESSIBILITY to if (accessibilityOn) "on" else "off",
        GroupFields.NOTIFICATIONS to if (notificationsOn) "on" else "off",
        GroupFields.STATUS_AT to now,
        GroupFields.LAST_CHECK_ID to checkId,
        GroupFields.LAST_CHECK_AT to now,
    )

    fun pushTokenFields(token: String, now: Any): Map<String, Any?> = mapOf(
        GroupFields.FCM_TOKEN to token,
        GroupFields.UPDATED_AT to now,
    )

    fun appEventFields(
        type: AppEventType,
        packageName: String,
        label: String,
        at: Any,
        detectedLate: Boolean,
        groupId: String,
    ): Map<String, Any?> = mapOf(
        GroupFields.TYPE to type.wire,
        GroupFields.PACKAGE_NAME to packageName,
        GroupFields.LABEL to label,
        GroupFields.AT to at,
        GroupFields.DETECTED_LATE to detectedLate,
        GroupFields.GROUP_ID to groupId,
    )

    fun removeFromGroupFields(): Map<String, Any?> = mapOf(
        GroupFields.GROUP_ID to null,
    )
}
