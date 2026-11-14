package com.dhananjay.livecast.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GroupFirestoreTest {

    @Test
    fun memberPathUsesLowercaseEmail() {
        assertEquals("groups/g1/members/kid@x.com", FirestorePaths.member("g1", " Kid@X.com "))
    }

    @Test
    fun paths() {
        assertEquals("devices/d1", FirestorePaths.device("d1"))
        assertEquals("groups/g1", FirestorePaths.group("g1"))
        assertEquals("groups/g1/members", FirestorePaths.members("g1"))
        assertEquals("devices/d1/private/push", FirestorePaths.privatePush("d1"))
        assertEquals("devices/d1/appEvents", FirestorePaths.appEvents("d1"))
    }

    @Test
    fun groupIdFromMemberPath() {
        assertEquals(
            "g1",
            FirestorePaths.groupIdFromMemberPath("projects/p/databases/(default)/documents/groups/g1/members/a@b.c")
        )
        assertEquals(
            "g1",
            FirestorePaths.groupIdFromMemberPath("groups/g1/members/a@b.c")
        )
        assertNull(FirestorePaths.groupIdFromMemberPath("devices/d1"))
    }

    @Test
    fun memberRoundTrip() {
        val data = mapOf(
            GroupFields.EMAIL to "a@b.c",
            GroupFields.ROLE to "supervisor",
            GroupFields.NAME to null,
            GroupFields.ADDED_AT to 1000L,
        )
        val member = groupMemberFrom("g1", "a@b.c", data)
        assertEquals(GroupMember("g1", "a@b.c", GroupRole.SUPERVISOR, null, 1000L), member)
    }

    @Test
    fun unknownRoleIsNull() {
        val data = mapOf(
            GroupFields.EMAIL to "a@b.c",
            GroupFields.ROLE to "admin",
            GroupFields.ADDED_AT to 1000L,
        )
        assertNull(groupMemberFrom("g1", "a@b.c", data))
    }

    @Test
    fun groupRoundTrip() {
        val data = mapOf(
            GroupFields.NAME to "Family",
            GroupFields.OWNER_UID to "owner-1",
            GroupFields.CREATED_AT to 1000L,
        )
        val group = groupFrom("g1", data)
        assertEquals(Group("g1", "Family", "owner-1", 1000L), group)
    }

    @Test
    fun groupMissingOwnerIsNull() {
        val dataWithoutOwner = mapOf(
            GroupFields.NAME to "Family",
            GroupFields.CREATED_AT to 1000L,
        )
        assertNull(groupFrom("g1", dataWithoutOwner))

        val dataWithoutName = mapOf(
            GroupFields.OWNER_UID to "owner-1",
            GroupFields.CREATED_AT to 1000L,
        )
        assertNull(groupFrom("g1", dataWithoutName))
    }

    @Test
    fun hostDeviceWithGroup() {
        val data = mapOf(
            GroupFields.DISPLAY_NAME to "Pixel",
            GroupFields.MODEL to "P8",
            GroupFields.GROUP_ID to "g1",
            GroupFields.ENROLLED_AT to 4000L,
        )
        val device = hostDeviceFrom("d1", data)
        assertNotNull(device)
        assertEquals("d1", device.id)
        assertEquals("Pixel", device.displayName)
        assertEquals("P8", device.model)
        assertEquals("g1", device.groupId)
        assertEquals(4000L, device.enrolledAtEpochMs)
    }

    @Test
    fun hostDeviceNullGroupId() {
        val data = mapOf(
            GroupFields.DISPLAY_NAME to "Pixel",
            GroupFields.MODEL to "P8",
            GroupFields.GROUP_ID to null,
        )
        val device = hostDeviceFrom("d1", data)
        assertNotNull(device)
        assertNull(device.groupId)
    }

    @Test
    fun enrollFieldsHaveNoPresence() {
        val device = HostDevice("d1", "Pixel", "P8")
        val fields = GroupWrites.enrollFields(device, "g1", 1000L)
        assertEquals(
            setOf(
                GroupFields.GROUP_ID,
                GroupFields.HOST_UID,
                GroupFields.DISPLAY_NAME,
                GroupFields.MODEL,
                GroupFields.ENROLLED_AT
            ),
            fields.keys
        )
        assertEquals("g1", fields[GroupFields.GROUP_ID])
        assertEquals("d1", fields[GroupFields.HOST_UID])
        assertEquals("Pixel", fields[GroupFields.DISPLAY_NAME])
        assertEquals("P8", fields[GroupFields.MODEL])
        assertEquals(1000L, fields[GroupFields.ENROLLED_AT])
    }

    @Test
    fun statusFieldsWireValues() {
        val fields = GroupWrites.statusFields(accessibilityOn = true, notificationsOn = false, now = 2000L)
        assertEquals("on", fields[GroupFields.ACCESSIBILITY])
        assertEquals("off", fields[GroupFields.NOTIFICATIONS])
        assertEquals(2000L, fields[GroupFields.STATUS_AT])
        assertEquals(
            setOf(GroupFields.ACCESSIBILITY, GroupFields.NOTIFICATIONS, GroupFields.STATUS_AT),
            fields.keys
        )
    }

    @Test
    fun checkReplyFieldsIncludeStatus() {
        val fields = GroupWrites.checkReplyFields("check-123", accessibilityOn = false, notificationsOn = true, now = 3000L)
        assertEquals("off", fields[GroupFields.ACCESSIBILITY])
        assertEquals("on", fields[GroupFields.NOTIFICATIONS])
        assertEquals(3000L, fields[GroupFields.STATUS_AT])
        assertEquals("check-123", fields[GroupFields.LAST_CHECK_ID])
        assertEquals(3000L, fields[GroupFields.LAST_CHECK_AT])
        assertEquals(
            setOf(
                GroupFields.ACCESSIBILITY,
                GroupFields.NOTIFICATIONS,
                GroupFields.STATUS_AT,
                GroupFields.LAST_CHECK_ID,
                GroupFields.LAST_CHECK_AT
            ),
            fields.keys
        )
    }

    @Test
    fun pushTokenFieldsKeys() {
        val fields = GroupWrites.pushTokenFields("fcm-tok-123", 4000L)
        assertEquals(
            setOf(GroupFields.FCM_TOKEN, GroupFields.UPDATED_AT),
            fields.keys
        )
        assertEquals("fcm-tok-123", fields[GroupFields.FCM_TOKEN])
        assertEquals(4000L, fields[GroupFields.UPDATED_AT])
    }

    @Test
    fun appEventFieldsKeys() {
        val fields = GroupWrites.appEventFields(
            type = AppEventType.INSTALLED,
            packageName = "com.example.app",
            label = "Example",
            at = 5000L,
            detectedLate = false,
            groupId = "g1"
        )
        assertEquals(
            setOf(
                GroupFields.TYPE,
                GroupFields.PACKAGE_NAME,
                GroupFields.LABEL,
                GroupFields.AT,
                GroupFields.DETECTED_LATE,
                GroupFields.GROUP_ID
            ),
            fields.keys
        )
        assertEquals("installed", fields[GroupFields.TYPE])
        assertEquals("com.example.app", fields[GroupFields.PACKAGE_NAME])
        assertEquals("Example", fields[GroupFields.LABEL])
        assertEquals(5000L, fields[GroupFields.AT])
        assertEquals(false, fields[GroupFields.DETECTED_LATE])
        assertEquals("g1", fields[GroupFields.GROUP_ID])
    }

    @Test
    fun hostDeviceFromReadsStatus() {
        val dataWithStatus = mapOf(
            GroupFields.DISPLAY_NAME to "Pixel",
            GroupFields.MODEL to "P8",
            GroupFields.GROUP_ID to "g1",
            GroupFields.ACCESSIBILITY to "on",
            GroupFields.NOTIFICATIONS to "off",
            GroupFields.STATUS_AT to 6000L,
            GroupFields.LAST_CHECK_ID to "c-1",
            GroupFields.LAST_CHECK_AT to 6001L,
        )
        val device1 = hostDeviceFrom("d1", dataWithStatus)
        assertNotNull(device1)
        assertEquals(ToggleState.ON, device1.accessibility)
        assertEquals(ToggleState.OFF, device1.notifications)
        assertEquals(6000L, device1.statusAtEpochMs)
        assertEquals("c-1", device1.lastCheckId)
        assertEquals(6001L, device1.lastCheckAtEpochMs)

        val dataMinimal = mapOf(
            GroupFields.DISPLAY_NAME to "Pixel",
            GroupFields.MODEL to "P8",
        )
        val device2 = hostDeviceFrom("d2", dataMinimal)
        assertNotNull(device2)
        assertEquals(ToggleState.UNKNOWN, device2.accessibility)
        assertEquals(ToggleState.UNKNOWN, device2.notifications)
        assertNull(device2.statusAtEpochMs)
        assertNull(device2.lastCheckId)
        assertNull(device2.lastCheckAtEpochMs)
    }

    @Test
    fun appEventFromParses() {
        val data = mapOf(
            GroupFields.TYPE to "installed",
            GroupFields.PACKAGE_NAME to "com.example.app",
            GroupFields.LABEL to "Example",
            GroupFields.AT to 5000L,
            GroupFields.DETECTED_LATE to true,
            GroupFields.GROUP_ID to "g1",
        )
        val event = appEventFrom("e1", data)
        assertNotNull(event)
        assertEquals("e1", event.id)
        assertEquals(AppEventType.INSTALLED, event.type)
        assertEquals("com.example.app", event.packageName)
        assertEquals("Example", event.label)
        assertEquals(5000L, event.atEpochMs)
        assertTrue(event.detectedLate)
        assertEquals("g1", event.groupId)
    }

    @Test
    fun appEventFromRejectsMissingGroup() {
        val data = mapOf(
            GroupFields.TYPE to "installed",
            GroupFields.PACKAGE_NAME to "com.example.app",
            GroupFields.LABEL to "Example",
            GroupFields.AT to 5000L,
        )
        assertNull(appEventFrom("e1", data))
    }

    @Test
    fun removeFieldsOnlyGroupId() {
        assertEquals(mapOf(GroupFields.GROUP_ID to null), GroupWrites.removeFromGroupFields())
    }

    @Test
    fun memberFieldsNormalizeEmail() {
        val fields = GroupWrites.memberFields(" A@B.co ", GroupRole.DEVICE, "Name", 1000L)
        assertEquals("a@b.co", fields[GroupFields.EMAIL])
        assertEquals("device", fields[GroupFields.ROLE])
        assertEquals("Name", fields[GroupFields.NAME])
        assertEquals(1000L, fields[GroupFields.ADDED_AT])
    }
}
