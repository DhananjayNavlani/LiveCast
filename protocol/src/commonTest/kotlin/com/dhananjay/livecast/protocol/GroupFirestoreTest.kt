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
    fun groupAndMembersPaths() {
        assertEquals("groups/g1", FirestorePaths.group("g1"))
        assertEquals("groups/g1/members", FirestorePaths.members("g1"))
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
            GroupFields.ONLINE to true,
            GroupFields.LAST_SEEN to 5000L,
            GroupFields.GROUP_ID to "g1",
            GroupFields.ENROLLED_AT to 4000L,
        )
        val device = hostDeviceFrom("d1", data)
        assertNotNull(device)
        assertEquals("d1", device.id)
        assertEquals("Pixel", device.displayName)
        assertEquals("P8", device.model)
        assertTrue(device.online)
        assertEquals(5000L, device.lastSeenEpochMs)
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
    fun enrollFieldsKeys() {
        val device = HostDevice("d1", "Pixel", "P8")
        val fields = GroupWrites.enrollFields(device, "g1", 1000L)
        assertEquals(
            setOf(
                GroupFields.GROUP_ID,
                GroupFields.HOST_UID,
                GroupFields.DISPLAY_NAME,
                GroupFields.MODEL,
                GroupFields.ONLINE,
                GroupFields.LAST_SEEN,
                GroupFields.ENROLLED_AT
            ),
            fields.keys
        )
        assertEquals(true, fields[GroupFields.ONLINE])
        assertEquals("g1", fields[GroupFields.GROUP_ID])
        assertEquals("d1", fields[GroupFields.HOST_UID])
        assertEquals("Pixel", fields[GroupFields.DISPLAY_NAME])
        assertEquals("P8", fields[GroupFields.MODEL])
        assertEquals(1000L, fields[GroupFields.LAST_SEEN])
        assertEquals(1000L, fields[GroupFields.ENROLLED_AT])
    }

    @Test
    fun heartbeatFieldsKeys() {
        val fields = GroupWrites.heartbeatFields(true, 2000L)
        assertEquals(setOf(GroupFields.ONLINE, GroupFields.LAST_SEEN), fields.keys)
        assertEquals(true, fields[GroupFields.ONLINE])
        assertEquals(2000L, fields[GroupFields.LAST_SEEN])
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
