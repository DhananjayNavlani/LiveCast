package com.dhananjay.livecast.agent

import com.dhananjay.livecast.group.DeviceIdentity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AccessibilityStateReporterTest {

    private class FakeDeviceIdentity(override var deviceId: String? = "dev1") : DeviceIdentity

    private class FakeEnrollmentState(var groupId: String? = "grp1") : EnrollmentState {
        override suspend fun currentGroupId(): String? = groupId
    }

    private class FakeDeviceStatusSource(
        var accessibility: Boolean = true,
        var notifications: Boolean = true
    ) : DeviceStatusSource {
        override fun accessibilityOn(): Boolean = accessibility
        override fun notificationsOn(): Boolean = notifications
    }

    private class FakeDeviceStatusWriter : DeviceStatusWriter {
        data class StatusWrite(
            val deviceId: String,
            val accessibilityOn: Boolean,
            val notificationsOn: Boolean
        )

        val writes = mutableListOf<StatusWrite>()

        override suspend fun writeStatus(
            deviceId: String,
            accessibilityOn: Boolean,
            notificationsOn: Boolean
        ): Result<Unit> {
            writes.add(StatusWrite(deviceId, accessibilityOn, notificationsOn))
            return Result.success(Unit)
        }

        override suspend fun writeCheckReply(
            deviceId: String,
            checkId: String,
            accessibilityOn: Boolean,
            notificationsOn: Boolean
        ): Result<Unit> = Result.success(Unit)
    }

    @Test
    fun startWritesOnWithNotificationsState() = runTest {
        val identity = FakeDeviceIdentity("dev1")
        val enrollment = FakeEnrollmentState("grp1")
        val status = FakeDeviceStatusSource(accessibility = true, notifications = true)
        val writer = FakeDeviceStatusWriter()
        val reporter = AccessibilityStateReporter(identity, enrollment, status, writer, this)

        reporter.start(this)
        advanceUntilIdle()

        assertEquals(1, writer.writes.size)
        val write = writer.writes.first()
        assertEquals("dev1", write.deviceId)
        assertTrue(write.accessibilityOn)
        assertTrue(write.notificationsOn)
    }

    @Test
    fun stopWritesOff() = runTest {
        val identity = FakeDeviceIdentity("dev1")
        val enrollment = FakeEnrollmentState("grp1")
        val status = FakeDeviceStatusSource(accessibility = true, notifications = false)
        val writer = FakeDeviceStatusWriter()
        val reporter = AccessibilityStateReporter(identity, enrollment, status, writer, this)

        reporter.stop()
        advanceUntilIdle()

        assertEquals(1, writer.writes.size)
        val write = writer.writes.first()
        assertEquals("dev1", write.deviceId)
        assertEquals(false, write.accessibilityOn)
        assertEquals(false, write.notificationsOn)
    }

    @Test
    fun notEnrolledWritesNothing() = runTest {
        val identity = FakeDeviceIdentity("dev1")
        val enrollment = FakeEnrollmentState(groupId = null)
        val status = FakeDeviceStatusSource(accessibility = true, notifications = true)
        val writer = FakeDeviceStatusWriter()
        val reporter = AccessibilityStateReporter(identity, enrollment, status, writer, this)

        reporter.start(this)
        reporter.stop()
        advanceUntilIdle()

        assertTrue(writer.writes.isEmpty())
    }
}
