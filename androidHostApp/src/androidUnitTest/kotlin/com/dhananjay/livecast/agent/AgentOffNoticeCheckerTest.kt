package com.dhananjay.livecast.agent

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class AgentOffNoticeCheckerTest {

    private class FakeEnrollmentState(
        var enrollmentStatus: EnrollmentStatus = EnrollmentStatus.Enrolled("grp1")
    ) : EnrollmentState {
        override suspend fun status(): EnrollmentStatus = enrollmentStatus
    }

    private class FakeDeviceStatusSource(
        var accessibility: Boolean = true,
        var notifications: Boolean = true
    ) : DeviceStatusSource {
        override fun accessibilityOn(): Boolean = accessibility
        override fun notificationsOn(): Boolean = notifications
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
    fun enrolledOffShows() = runTest {
        val enrollment = FakeEnrollmentState(EnrollmentStatus.Enrolled("grp1"))
        val status = FakeDeviceStatusSource(accessibility = false)
        val notifications = FakeAgentNotifications()
        val checker = AgentOffNoticeChecker(enrollment, status, notifications)

        checker.check()

        assertEquals(1, notifications.showAccessibilityOffCalls)
    }

    @Test
    fun onDoesNotShow() = runTest {
        val enrollment = FakeEnrollmentState(EnrollmentStatus.Enrolled("grp1"))
        val status = FakeDeviceStatusSource(accessibility = true)
        val notifications = FakeAgentNotifications()
        val checker = AgentOffNoticeChecker(enrollment, status, notifications)

        checker.check()

        assertEquals(0, notifications.showAccessibilityOffCalls)
    }

    @Test
    fun notEnrolledDoesNotShow() = runTest {
        val enrollment = FakeEnrollmentState(EnrollmentStatus.NotEnrolled)
        val status = FakeDeviceStatusSource(accessibility = false)
        val notifications = FakeAgentNotifications()
        val checker = AgentOffNoticeChecker(enrollment, status, notifications)

        checker.check()

        assertEquals(0, notifications.showAccessibilityOffCalls)
    }
}
