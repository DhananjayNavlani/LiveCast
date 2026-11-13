package com.dhananjay.livecast.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class GroupRole {
    @SerialName("owner") OWNER,
    @SerialName("supervisor") SUPERVISOR,
    @SerialName("device") DEVICE,
}

@Serializable
data class Group(
    val id: String,
    val name: String,
    val ownerUid: String,
    val createdAtEpochMs: Long,
)

@Serializable
data class GroupMember(
    val groupId: String,
    val email: String,
    val role: GroupRole,
    val name: String?,
    val addedAtEpochMs: Long,
)

@Serializable
data class GroupMembership(
    val group: Group,
    val myRole: GroupRole,
)

@Serializable
data class HostDevice(
    val id: String,
    val displayName: String,
    val model: String,
    val online: Boolean = false,
    val lastSeenEpochMs: Long = 0,
    val groupId: String? = null,
    val enrolledAtEpochMs: Long? = null,
)

object GroupRules {
    const val ONLINE_WINDOW_MS = 120_000L
    const val HEARTBEAT_INTERVAL_MS = 45_000L
    const val PRESENCE_TICK_MS = 15_000L
    const val GROUP_NAME_MAX = 60

    private val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    fun normalizeEmail(email: String): String = email.trim().lowercase()

    fun isValidEmail(email: String): Boolean = EMAIL_REGEX.matches(normalizeEmail(email))

    fun normalizeGroupName(name: String): String? {
        val trimmed = name.trim()
        return if (trimmed.isEmpty() || trimmed.length > GROUP_NAME_MAX) null else trimmed
    }

    fun isOnline(device: HostDevice, nowEpochMs: Long): Boolean {
        return device.online && (nowEpochMs - device.lastSeenEpochMs <= ONLINE_WINDOW_MS)
    }

    fun displayName(member: GroupMember): String {
        return if (!member.name.isNullOrBlank()) member.name else member.email
    }

    fun supervisorsOf(members: List<GroupMember>): List<GroupMember> {
        val nonDevices = members.filter { it.role != GroupRole.DEVICE }
        val owners = nonDevices.filter { it.role == GroupRole.OWNER }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { displayName(it) })
        val supervisors = nonDevices.filter { it.role == GroupRole.SUPERVISOR }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { displayName(it) })
        return owners + supervisors
    }

    fun viewableGroupIds(memberships: List<GroupMembership>): List<String> {
        return memberships
            .filter { it.myRole == GroupRole.OWNER || it.myRole == GroupRole.SUPERVISOR }
            .map { it.group.id }
            .distinct()
    }

    fun roleWire(role: GroupRole): String = when (role) {
        GroupRole.OWNER -> "owner"
        GroupRole.SUPERVISOR -> "supervisor"
        GroupRole.DEVICE -> "device"
    }

    fun roleFrom(wire: String?): GroupRole? = when (wire) {
        "owner" -> GroupRole.OWNER
        "supervisor" -> GroupRole.SUPERVISOR
        "device" -> GroupRole.DEVICE
        else -> null
    }
}
