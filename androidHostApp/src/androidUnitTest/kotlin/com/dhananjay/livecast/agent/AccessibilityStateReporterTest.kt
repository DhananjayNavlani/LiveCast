package com.dhananjay.livecast.agent

import com.dhananjay.livecast.group.DeviceIdentity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AccessibilityStateReporterTest {

    private class FakeDeviceIdentity(override var deviceId: String? = "dev1") : DeviceIdentity

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

    private class FakeDeviceStatusWriter : DeviceStatusWriter {
        data class StatusWrite(
            val deviceId: String,
            val accessibilityOn: Boolean,
            val notificationsOn: Boolean
        )

        val writes = mutableListOf<StatusWrite>()
        var offWriteGate: CompletableDeferred<Unit>? = null

        override suspend fun writeStatus(
            deviceId: String,
            accessibilityOn: Boolean,
            notificationsOn: Boolean
        ): Result<Unit> {
            if (!accessibilityOn) {
                offWriteGate?.await()
            }
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
        val enrollment = FakeEnrollmentState(EnrollmentStatus.Enrolled("grp1"))
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
        reporter.stop(reportOff = false)
    }

    @Test
    fun stopWritesOff() = runTest {
        val identity = FakeDeviceIdentity("dev1")
        val enrollment = FakeEnrollmentState(EnrollmentStatus.Enrolled("grp1"))
        val status = FakeDeviceStatusSource(accessibility = true, notifications = false)
        val writer = FakeDeviceStatusWriter()
        val reporter = AccessibilityStateReporter(identity, enrollment, status, writer, this)

        reporter.stop(reportOff = true)
        advanceUntilIdle()

        assertEquals(1, writer.writes.size)
        val write = writer.writes.first()
        assertEquals("dev1", write.deviceId)
        assertFalse(write.accessibilityOn)
        assertFalse(write.notificationsOn)
    }

    @Test
    fun notEnrolledWritesNothing() = runTest {
        val identity = FakeDeviceIdentity("dev1")
        val enrollment = FakeEnrollmentState(EnrollmentStatus.NotEnrolled)
        val status = FakeDeviceStatusSource(accessibility = true, notifications = true)
        val writer = FakeDeviceStatusWriter()
        val reporter = AccessibilityStateReporter(identity, enrollment, status, writer, this)

        reporter.start(this)
        reporter.stop(reportOff = true)
        advanceUntilIdle()

        assertTrue(writer.writes.isEmpty())
    }

    @Test
    fun restartWritesNoOff() = runTest {
        val identity = FakeDeviceIdentity("dev1")
        val enrollment = FakeEnrollmentState(EnrollmentStatus.Enrolled("grp1"))
        val status = FakeDeviceStatusSource(accessibility = true, notifications = true)
        val writer = FakeDeviceStatusWriter()
        val reporter = AccessibilityStateReporter(identity, enrollment, status, writer, this)

        reporter.stop(reportOff = false)
        advanceUntilIdle()

        assertTrue(writer.writes.isEmpty())
    }

    @Test
    fun startAfterStopEndsOn() = runTest {
        val identity = FakeDeviceIdentity("dev1")
        val enrollment = FakeEnrollmentState(EnrollmentStatus.Enrolled("grp1"))
        val status = FakeDeviceStatusSource(accessibility = true, notifications = true)
        val writer = FakeDeviceStatusWriter()
        val offGate = CompletableDeferred<Unit>()
        writer.offWriteGate = offGate
        val reporter = AccessibilityStateReporter(identity, enrollment, status, writer, this)

        reporter.stop(reportOff = true)
        reporter.start(this)

        offGate.complete(Unit)
        advanceUntilIdle()

        assertEquals(2, writer.writes.size)
        assertFalse(writer.writes[0].accessibilityOn)
        assertTrue(writer.writes[1].accessibilityOn)
        assertEquals("dev1", writer.writes.last().deviceId)
        assertTrue(writer.writes.last().accessibilityOn)
        reporter.stop(reportOff = false)
    }
}
