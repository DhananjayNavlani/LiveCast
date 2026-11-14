package com.dhananjay.livecast.agent

import com.dhananjay.livecast.group.GroupMembersDirectory
import com.dhananjay.livecast.protocol.GroupMember
import com.dhananjay.livecast.protocol.GroupRole
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SupervisionNotifierTest {

    private class FakeEnrollmentState(
        var enrollmentStatus: EnrollmentStatus = EnrollmentStatus.Enrolled("grp1")
    ) : EnrollmentState {
        override suspend fun status(): EnrollmentStatus = enrollmentStatus
    }

    private class FakeGroupMembersDirectory(
        supervisors: List<GroupMember> = emptyList()
    ) : GroupMembersDirectory {
        val flow = MutableStateFlow(supervisors)
        override fun observeSupervisors(groupId: String): Flow<List<GroupMember>> = flow
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
        val enrollment = FakeEnrollmentState(EnrollmentStatus.Enrolled("grp1"))
        // SUPERVISOR listed before OWNER to verify OWNER is preferred over list order
        val members = FakeGroupMembersDirectory(
            listOf(
                GroupMember("grp1", "supervisor@example.com", GroupRole.SUPERVISOR, "Bob", 1000L),
                GroupMember("grp1", "owner@example.com", GroupRole.OWNER, "Alice", 1000L)
            )
        )
        val notifications = FakeAgentNotifications()
        val notifier = SupervisionNotifier(enrollment, members, notifications, this)

        notifier.start(this)
        advanceUntilIdle()

        assertEquals(listOf("Alice"), notifications.showSupervisedCalls)
        assertEquals(0, notifications.showAccessibilityOffCalls)
        notifier.stop(reportOff = false)
    }

    @Test
    fun ownerWithoutNameUsesEmail() = runTest {
        val enrollment = FakeEnrollmentState(EnrollmentStatus.Enrolled("grp1"))
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
        notifier.stop(reportOff = false)
    }

    @Test
    fun noOwnerUsesFirstSupervisor() = runTest {
        val enrollment = FakeEnrollmentState(EnrollmentStatus.Enrolled("grp1"))
        val members = FakeGroupMembersDirectory(
            listOf(
                GroupMember("grp1", "sup1@example.com", GroupRole.SUPERVISOR, "Bob", 1000L),
                GroupMember("grp1", "sup2@example.com", GroupRole.SUPERVISOR, "Charlie", 1000L)
            )
        )
        val notifications = FakeAgentNotifications()
        val notifier = SupervisionNotifier(enrollment, members, notifications, this)

        notifier.start(this)
        advanceUntilIdle()

        assertEquals(listOf("Bob"), notifications.showSupervisedCalls)
        notifier.stop(reportOff = false)
    }

    @Test
    fun laterEmissionWithOwnerUpdatesName() = runTest {
        val enrollment = FakeEnrollmentState(EnrollmentStatus.Enrolled("grp1"))
        val members = FakeGroupMembersDirectory(
            listOf(
                GroupMember("grp1", "sup1@example.com", GroupRole.SUPERVISOR, "Bob", 1000L)
            )
        )
        val notifications = FakeAgentNotifications()
        val notifier = SupervisionNotifier(enrollment, members, notifications, this)

        notifier.start(this)
        advanceUntilIdle()
        assertEquals(listOf("Bob"), notifications.showSupervisedCalls)

        // New emission with OWNER added
        members.flow.value = listOf(
            GroupMember("grp1", "sup1@example.com", GroupRole.SUPERVISOR, "Bob", 1000L),
            GroupMember("grp1", "owner@example.com", GroupRole.OWNER, "Alice", 1000L)
        )
        advanceUntilIdle()

        assertEquals(listOf("Bob", "Alice"), notifications.showSupervisedCalls)
        notifier.stop(reportOff = false)
    }

    @Test
    fun startCancelsStaleOffNotice() = runTest {
        val enrollment = FakeEnrollmentState(EnrollmentStatus.Enrolled("grp1"))
        val members = FakeGroupMembersDirectory(
            listOf(
                GroupMember("grp1", "owner@example.com", GroupRole.OWNER, "Alice", 1000L)
            )
        )
        val notifications = FakeAgentNotifications()
        val notifier = SupervisionNotifier(enrollment, members, notifications, this)

        notifier.start(this)
        advanceUntilIdle()

        assertEquals(1, notifications.cancelCalls)
        notifier.stop(reportOff = false)
    }

    @Test
    fun stopShowsOffNotice() = runTest {
        val enrollment = FakeEnrollmentState(EnrollmentStatus.Enrolled("grp1"))
        val members = FakeGroupMembersDirectory(
            listOf(
                GroupMember("grp1", "owner@example.com", GroupRole.OWNER, "Alice", 1000L)
            )
        )
        val notifications = FakeAgentNotifications()
        val notifier = SupervisionNotifier(enrollment, members, notifications, this)

        notifier.start(this)
        advanceUntilIdle()

        notifier.stop(reportOff = true)
        advanceUntilIdle()

        assertEquals(1, notifications.showAccessibilityOffCalls)
    }

    @Test
    fun notEnrolledShowsNothing() = runTest {
        val enrollment = FakeEnrollmentState(EnrollmentStatus.NotEnrolled)
        val members = FakeGroupMembersDirectory(
            listOf(
                GroupMember("grp1", "owner@example.com", GroupRole.OWNER, "Alice", 1000L)
            )
        )
        val notifications = FakeAgentNotifications()
        val notifier = SupervisionNotifier(enrollment, members, notifications, this)

        notifier.start(this)
        notifier.stop(reportOff = true)
        advanceUntilIdle()

        assertTrue(notifications.showSupervisedCalls.isEmpty())
        assertEquals(0, notifications.showAccessibilityOffCalls)
    }

    @Test
    fun restartShowsNoOffNotice() = runTest {
        val enrollment = FakeEnrollmentState(EnrollmentStatus.Enrolled("grp1"))
        val members = FakeGroupMembersDirectory(
            listOf(
                GroupMember("grp1", "owner@example.com", GroupRole.OWNER, "Alice", 1000L)
            )
        )
        val notifications = FakeAgentNotifications()
        val notifier = SupervisionNotifier(enrollment, members, notifications, this)

        notifier.start(this)
        advanceUntilIdle()

        notifier.stop(reportOff = false)
        advanceUntilIdle()

        assertEquals(0, notifications.showAccessibilityOffCalls)
    }

    @Test
    fun startAfterStopEndsSupervised() = runTest {
        val enrollment = FakeEnrollmentState(EnrollmentStatus.Enrolled("grp1"))
        val members = FakeGroupMembersDirectory(
            listOf(
                GroupMember("grp1", "owner@example.com", GroupRole.OWNER, "Alice", 1000L)
            )
        )
        val notifications = FakeAgentNotifications()
        val notifier = SupervisionNotifier(enrollment, members, notifications, this)

        notifier.stop(reportOff = true)
        notifier.start(this)
        advanceUntilIdle()

        assertEquals(1, notifications.showAccessibilityOffCalls)
        assertEquals(1, notifications.cancelCalls)
        assertEquals(listOf("Alice"), notifications.showSupervisedCalls)
        notifier.stop(reportOff = false)
    }
}
