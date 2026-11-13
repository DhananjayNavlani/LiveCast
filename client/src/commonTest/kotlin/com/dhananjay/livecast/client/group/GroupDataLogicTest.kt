package com.dhananjay.livecast.client.group

import com.dhananjay.livecast.auth.AuthState
import com.dhananjay.livecast.auth.SessionProvider
import com.dhananjay.livecast.auth.User
import com.dhananjay.livecast.protocol.Group
import com.dhananjay.livecast.protocol.GroupFields
import com.dhananjay.livecast.protocol.GroupRole
import com.dhananjay.livecast.protocol.HostDevice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

private class LogicTestSessionProvider(
    override var currentUser: User? = null,
) : SessionProvider {
    private val _authState = MutableStateFlow<AuthState>(
        currentUser?.let { AuthState.Authenticated(it) } ?: AuthState.Unauthenticated
    )
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    fun setUser(user: User?) {
        currentUser = user
        _authState.value = user?.let { AuthState.Authenticated(it) } ?: AuthState.Unauthenticated
    }

    override suspend fun getIdToken(forceRefresh: Boolean): String? = "fake-token"
}

class GroupDataLogicTest {

    @Test
    fun validateCreateGroupCases() {
        val nullResult = validateCreateGroup(null, "Family")
        assertTrue(nullResult.isFailure)
        assertIs<GroupError.NotSignedIn>(nullResult.exceptionOrNull())

        val anonResult = validateCreateGroup(
            User(id = "u1", email = "a@b.c", displayName = null, isAnonymous = true),
            "Family",
        )
        assertTrue(anonResult.isFailure)
        assertIs<GroupError.NotSignedIn>(anonResult.exceptionOrNull())

        val nullEmailResult = validateCreateGroup(
            User(id = "u1", email = null, displayName = null, emailVerified = true),
            "Family",
        )
        assertTrue(nullEmailResult.isFailure)
        assertIs<GroupError.NotSignedIn>(nullEmailResult.exceptionOrNull())

        val blankEmailResult = validateCreateGroup(
            User(id = "u1", email = "   ", displayName = null, emailVerified = true),
            "Family",
        )
        assertTrue(blankEmailResult.isFailure)
        assertIs<GroupError.NotSignedIn>(blankEmailResult.exceptionOrNull())

        val unverifiedResult = validateCreateGroup(
            User(id = "u1", email = "a@b.c", displayName = null, emailVerified = false),
            "Family",
        )
        assertTrue(unverifiedResult.isFailure)
        assertIs<GroupError.EmailNotVerified>(unverifiedResult.exceptionOrNull())

        val blankNameResult = validateCreateGroup(
            User(id = "u1", email = "a@b.c", displayName = null, emailVerified = true),
            "   ",
        )
        assertTrue(blankNameResult.isFailure)
        assertIs<IllegalArgumentException>(blankNameResult.exceptionOrNull())

        val okResult = validateCreateGroup(
            User(id = "u1", email = " Kid@Example.COM ", displayName = "Kid", emailVerified = true),
            "  Family  ",
        )
        assertTrue(okResult.isSuccess)
        val input = okResult.getOrThrow()
        assertEquals("u1", input.ownerUid)
        assertEquals("kid@example.com", input.ownerEmail)
        assertEquals("Kid", input.ownerName)
        assertEquals("Family", input.groupName)
    }

    @Test
    fun assembleSkipsUnloadableGroup() = runTest {
        val docs = listOf(
            MemberDoc(
                path = "groups/g1/members/a@b.c",
                id = "a@b.c",
                data = mapOf(GroupFields.EMAIL to "a@b.c", GroupFields.ROLE to "owner", GroupFields.ADDED_AT to 100L),
            ),
            MemberDoc(
                path = "groups/g2/members/a@b.c",
                id = "a@b.c",
                data = mapOf(GroupFields.EMAIL to "a@b.c", GroupFields.ROLE to "supervisor", GroupFields.ADDED_AT to 200L),
            ),
            MemberDoc(
                path = "groups/g3/members/a@b.c",
                id = "a@b.c",
                data = mapOf(GroupFields.EMAIL to "a@b.c", GroupFields.ROLE to "device", GroupFields.ADDED_AT to 300L),
            ),
        )

        val memberships = assembleMemberships(docs) { groupId ->
            when (groupId) {
                "g1" -> Group("g1", "Group 1", "u1", 50L)
                "g2" -> throw IllegalStateException("Load failed")
                else -> null
            }
        }

        assertEquals(1, memberships.size)
        assertEquals("g1", memberships[0].group.id)
        assertEquals(GroupRole.OWNER, memberships[0].myRole)

        assertFailsWith<CancellationException> {
            assembleMemberships(docs) {
                throw CancellationException("cancelled")
            }
        }
    }

    @Test
    fun mergeDedupesAndSorts() {
        val d1 = HostDevice(id = "d1", displayName = "zebra", model = "m1")
        val d2 = HostDevice(id = "d2", displayName = "Alpha", model = "m2")
        val d3 = HostDevice(id = "d1", displayName = "zebra", model = "m1") // duplicate id
        val d4 = HostDevice(id = "d3", displayName = "beta", model = "m3")

        val merged = mergeDevices(listOf(listOf(d1, d2), listOf(d3, d4)))
        assertEquals(3, merged.size)
        assertEquals("d2", merged[0].id) // "Alpha"
        assertEquals("d3", merged[1].id) // "beta"
        assertEquals("d1", merged[2].id) // "zebra"
    }

    @Test
    fun verifiedEmailEmitsOnlyForVerifiedUsers() = runTest {
        val session = LogicTestSessionProvider()
        val emitted = mutableListOf<String?>()
        val job = launch {
            session.verifiedEmail().toList(emitted)
        }
        testScheduler.advanceUntilIdle()
        assertEquals(listOf<String?>(null), emitted)

        session.setUser(User("u1", "kid@example.com", "Kid", emailVerified = false))
        testScheduler.advanceUntilIdle()
        assertEquals(listOf<String?>(null), emitted)


        session.setUser(User("u1", " Kid@Example.COM ", "Kid", emailVerified = true))
        testScheduler.advanceUntilIdle()
        assertEquals(listOf(null, "kid@example.com"), emitted)

        session.setUser(User("u2", null, null, isAnonymous = true))
        testScheduler.advanceUntilIdle()
        assertEquals(listOf(null, "kid@example.com", null), emitted)

        session.setUser(null)
        testScheduler.advanceUntilIdle()
        assertEquals(listOf(null, "kid@example.com", null), emitted)

        job.cancel()
    }

}
