package com.dhananjay.livecast.protocol

object FirestorePaths {
    const val DEVICES = "devices"
    const val GROUPS = "groups"
    const val MEMBERS = "members"
    const val PRIVATE = "private"
    const val APP_EVENTS = "appEvents"

    private val MEMBER_PATH_REGEX = Regex("""(?:^|/)groups/([^/]+)/members/([^/]+?)(?:/|$)""")

    fun device(deviceId: String): String = "$DEVICES/$deviceId"
    fun group(groupId: String): String = "$GROUPS/$groupId"
    fun members(groupId: String): String = "$GROUPS/$groupId/$MEMBERS"
    fun member(groupId: String, email: String): String = "$GROUPS/$groupId/$MEMBERS/${GroupRules.normalizeEmail(email)}"
    fun privatePush(uid: String): String = "$DEVICES/$uid/$PRIVATE/push"
    fun appEvents(uid: String): String = "$DEVICES/$uid/$APP_EVENTS"
    fun groupIdFromMemberPath(path: String): String? =
        MEMBER_PATH_REGEX.findAll(path).lastOrNull()?.groupValues?.get(1)
}
