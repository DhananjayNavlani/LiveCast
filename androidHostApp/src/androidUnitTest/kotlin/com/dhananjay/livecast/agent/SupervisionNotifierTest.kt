package com.dhananjay.livecast.agent

import com.dhananjay.livecast.group.GroupMembersDirectory
import com.dhananjay.livecast.protocol.GroupMember
import com.dhananjay.livecast.protocol.GroupRole
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SupervisionNotifierTest {

    private class FakeEnrollmentState(var groupId: String? = "grp1") : EnrollmentState {
        override suspend fun currentGroupId(): String? = groupId
    }

    private class FakeGroupMembersDirectory(
        var supervisors: List<GroupMember> = emptyList()
    ) : GroupMembersDirectory {
        override fun observeSupervisors(groupId: String): Flow<List<GroupMember>> = flowOf(supervisors)
    }

    private class FakeAgentNotifications : AgentNotifications {
        val showSupervisedCalls = mutableListOf<String>()
        var showAccessibilityOffCalls = 0
        var cancelCalls = 0

        override fun showSupervised(byName: String) {
            showSupervisedCalls.add(byName)
        }

        override fun showAccessibilityOff() {
            showAccessibilityOffCalls++
        }

        override fun cancel() {
            cancelCalls++
        }
    }

    @Test
    fun startShowsOwnerName() = runTest {
        val enrollment = FakeEnrollmentState("grp1")
        val members = FakeGroupMembersDirectory(
            listOf(
                GroupMember("grp1", "owner@example.com", GroupRole.OWNER, "Alice", 1000L),
                GroupMember("grp1", "supervisor@example.com", GroupRole.SUPERVISOR, "Bob", 1000L)
            )
        )
        val notifications = FakeAgentNotifications()
        val notifier = SupervisionNotifier(enrollment, members, notifications, this)

        notifier.start(this)
        advanceUntilIdle()

        assertEquals(listOf("Alice"), notifications.showSupervisedCalls)
        assertEquals(0, notifications.showAccessibilityOffCalls)
    }

    @Test
    fun ownerWithoutNameUsesEmail() = runTest {
        val enrollment = FakeEnrollmentState("grp1")
        val members = FakeGroupMembersDirectory(
            listOf(
                GroupMember("grp1", "owner@example.com", GroupRole.OWNER, "", 1000L)
            )
        )
        val notifications = FakeAgentNotifications()
        val notifier = SupervisionNotifier(enrollment, members, notifications, this)

        notifier.start(this)
        advanceUntilIdle()

        assertEquals(listOf("owner@example.com"), notifications.showSupervisedCalls)
    }

    @Test
    fun stopShowsOffNotice() = runTest {
        val enrollment = FakeEnrollmentState("grp1")
        val members = FakeGroupMembersDirectory(
            listOf(
                GroupMember("grp1", "owner@example.com", GroupRole.OWNER, "Alice", 1000L)
            )
        )
        val notifications = FakeAgentNotifications()
        val notifier = SupervisionNotifier(enrollment, members, notifications, this)

        notifier.start(this)
        advanceUntilIdle()

        notifier.stop()
        advanceUntilIdle()

        assertEquals(1, notifications.showAccessibilityOffCalls)
    }

    @Test
    fun notEnrolledShowsNothing() = runTest {
        val enrollment = FakeEnrollmentState(groupId = null)
        val members = FakeGroupMembersDirectory(
            listOf(
                GroupMember("grp1", "owner@example.com", GroupRole.OWNER, "Alice", 1000L)
            )
        )
        val notifications = FakeAgentNotifications()
        val notifier = SupervisionNotifier(enrollment, members, notifications, this)

        notifier.start(this)
        notifier.stop()
        advanceUntilIdle()

        assertTrue(notifications.showSupervisedCalls.isEmpty())
        assertEquals(0, notifications.showAccessibilityOffCalls)
    }
}
