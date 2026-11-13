package com.dhananjay.livecast.protocol

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GroupRulesTest {

    @Test
    fun onlineWindowBoundary() {
        val now = 1_000_000L

        // online + lastSeen 880_000 -> true (now - lastSeen = 120_000 <= 120_000)
        val deviceOnline1 = HostDevice("d1", "Pixel", "P8", online = true, lastSeenEpochMs = 880_000L)
        assertTrue(GroupRules.isOnline(deviceOnline1, now))

        // online + lastSeen 879_999 -> false (now - lastSeen = 120_001 > 120_000)
        val deviceOnline2 = HostDevice("d2", "Pixel", "P8", online = true, lastSeenEpochMs = 879_999L)
        assertFalse(GroupRules.isOnline(deviceOnline2, now))

        // online = false + lastSeen = now -> false
        val deviceOffline = HostDevice("d3", "Pixel", "P8", online = false, lastSeenEpochMs = now)
        assertFalse(GroupRules.isOnline(deviceOffline, now))
    }

    @Test
    fun supervisorsOfOrdersOwnerFirstAndDropsDevices() {
        val members = listOf(
            GroupMember("g1", "bob@x.com", GroupRole.SUPERVISOR, "bob", 100L),
            GroupMember("g1", "kid@x.com", GroupRole.DEVICE, "kid", 100L),
            GroupMember("g1", "zed@x.com", GroupRole.OWNER, "Zed", 100L),
            GroupMember("g1", "amy@x.com", GroupRole.SUPERVISOR, "Amy", 100L),
        )
        val supervisors = GroupRules.supervisorsOf(members)
        assertEquals(listOf("Zed", "Amy", "bob"), supervisors.map { GroupRules.displayName(it) })
    }

    @Test
    fun displayNameFallsBackToEmail() {
        val memberBlankName = GroupMember("g1", "kid@x.com", GroupRole.DEVICE, "  ", 100L)
        assertEquals("kid@x.com", GroupRules.displayName(memberBlankName))

        val memberNullName = GroupMember("g1", "kid@x.com", GroupRole.DEVICE, null, 100L)
        assertEquals("kid@x.com", GroupRules.displayName(memberNullName))

        val memberWithName = GroupMember("g1", "kid@x.com", GroupRole.DEVICE, "Kiddo", 100L)
        assertEquals("Kiddo", GroupRules.displayName(memberWithName))
    }

    @Test
    fun normalizeEmail() {
        assertEquals("kid@x.com", GroupRules.normalizeEmail("  KiD@X.Com  "))
    }

    @Test
    fun isValidEmail() {
        assertTrue(GroupRules.isValidEmail("kid@x.com"))
        assertTrue(GroupRules.isValidEmail(" Kid@x.com "))
        assertFalse(GroupRules.isValidEmail("kid@x"))
        assertFalse(GroupRules.isValidEmail("@x.com"))
        assertFalse(GroupRules.isValidEmail("a b@x.com"))
    }

    @Test
    fun normalizeGroupName() {
        assertEquals("Family", GroupRules.normalizeGroupName("  Family "))
        assertNull(GroupRules.normalizeGroupName("   "))
        assertNull(GroupRules.normalizeGroupName("a".repeat(61)))
        assertEquals("a".repeat(60), GroupRules.normalizeGroupName("a".repeat(60)))
    }

    @Test
    fun viewableGroupIds() {
        val m1 = GroupMembership(Group("g1", "G1", "o1", 0L), GroupRole.OWNER)
        val m2 = GroupMembership(Group("g2", "G2", "o2", 0L), GroupRole.DEVICE)
        val m3 = GroupMembership(Group("g3", "G3", "o3", 0L), GroupRole.SUPERVISOR)
        val m4 = GroupMembership(Group("g1", "G1", "o1", 0L), GroupRole.SUPERVISOR)
        assertEquals(listOf("g1", "g3"), GroupRules.viewableGroupIds(listOf(m1, m2, m3, m4)))
    }

    @Test
    fun newGroupIdShape() {
        val id1 = GroupWrites.newGroupId(Random(1))
        val id2 = GroupWrites.newGroupId(Random(2))
        assertTrue(Regex("^[A-Za-z0-9]{20}$").matches(id1))
        assertTrue(Regex("^[A-Za-z0-9]{20}$").matches(id2))
        assertNotEquals(id1, id2)
    }
}
