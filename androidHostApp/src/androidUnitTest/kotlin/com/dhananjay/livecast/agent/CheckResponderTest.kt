package com.dhananjay.livecast.agent

import com.dhananjay.livecast.group.DeviceIdentity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CheckResponderTest {

    private class FakeDeviceIdentity(override var deviceId: String? = "dev1") : DeviceIdentity

    private class FakeDeviceStatusSource(
        var accessibility: Boolean = true,
        var notifications: Boolean = true
    ) : DeviceStatusSource {
        override fun accessibilityOn(): Boolean = accessibility
        override fun notificationsOn(): Boolean = notifications
    }

    private class FakeDeviceStatusWriter : DeviceStatusWriter {
        data class CheckReply(
            val deviceId: String,
            val checkId: String,
            val accessibilityOn: Boolean,
            val notificationsOn: Boolean
        )

        val replies = mutableListOf<CheckReply>()
        var delayMs: Long = 0L
        var shouldFail: Boolean = false

        override suspend fun writeStatus(
            deviceId: String,
            accessibilityOn: Boolean,
            notificationsOn: Boolean
        ): Result<Unit> = Result.success(Unit)

        override suspend fun writeCheckReply(
            deviceId: String,
            checkId: String,
            accessibilityOn: Boolean,
            notificationsOn: Boolean
        ): Result<Unit> {
            if (delayMs > 0) {
                delay(delayMs)
            }
            if (shouldFail) {
                return Result.failure(RuntimeException("Write error"))
            }
            replies.add(CheckReply(deviceId, checkId, accessibilityOn, notificationsOn))
            return Result.success(Unit)
        }
    }

    private class FakeAgentNotifications : AgentNotifications {
        var showSupervisedCalls = mutableListOf<String>()
        var showAccessibilityOffCalls = 0
        var cancelCalls = 0
        var shouldThrowAccessibilityOff = false

        override fun showSupervised(byName: String) {
            showSupervisedCalls.add(byName)
        }

        override fun showAccessibilityOff() {
            showAccessibilityOffCalls++
            if (shouldThrowAccessibilityOff) {
                throw RuntimeException("Notification failed")
            }
        }

        override fun cancel() {
            cancelCalls++
        }
    }

    @Test
    fun writesReplyWithStatus() = runTest {
        val identity = FakeDeviceIdentity("dev1")
        val status = FakeDeviceStatusSource(accessibility = true, notifications = true)
        val writer = FakeDeviceStatusWriter()
        val notifications = FakeAgentNotifications()
        val responder = CheckResponder(identity, status, writer, notifications)

        val result = responder.respond("check1")

        assertTrue(result.isSuccess)
        assertEquals(1, writer.replies.size)
        val reply = writer.replies.first()
        assertEquals("dev1", reply.deviceId)
        assertEquals("check1", reply.checkId)
        assertTrue(reply.accessibilityOn)
        assertTrue(reply.notificationsOn)
        assertEquals(0, notifications.showAccessibilityOffCalls)
    }

    @Test
    fun accessibilityOffShowsNotice() = runTest {
        val identity = FakeDeviceIdentity("dev1")
        val status = FakeDeviceStatusSource(accessibility = false, notifications = true)
        val writer = FakeDeviceStatusWriter()
        val notifications = FakeAgentNotifications()
        val responder = CheckResponder(identity, status, writer, notifications)

        val result = responder.respond("check1")

        assertTrue(result.isSuccess)
        assertEquals(1, notifications.showAccessibilityOffCalls)
        assertEquals(1, writer.replies.size)
        assertFalse(writer.replies.first().accessibilityOn)
    }

    @Test
    fun accessibilityOnNoNotice() = runTest {
        val identity = FakeDeviceIdentity("dev1")
        val status = FakeDeviceStatusSource(accessibility = true, notifications = true)
        val writer = FakeDeviceStatusWriter()
        val notifications = FakeAgentNotifications()
        val responder = CheckResponder(identity, status, writer, notifications)

        val result = responder.respond("check1")

        assertTrue(result.isSuccess)
        assertEquals(0, notifications.showAccessibilityOffCalls)
    }

    @Test
    fun noDeviceIdFails() = runTest {
        val identity = FakeDeviceIdentity(null)
        val status = FakeDeviceStatusSource(accessibility = true, notifications = true)
        val writer = FakeDeviceStatusWriter()
        val notifications = FakeAgentNotifications()
        val responder = CheckResponder(identity, status, writer, notifications)

        val result = responder.respond("check1")

        assertTrue(result.isFailure)
        assertEquals(0, writer.replies.size)
    }

    @Test
    fun writeTimeoutFails() = runTest {
        val identity = FakeDeviceIdentity("dev1")
        val status = FakeDeviceStatusSource(accessibility = true, notifications = true)
        val writer = FakeDeviceStatusWriter().apply { delayMs = 60_000L }
        val notifications = FakeAgentNotifications()
        val responder = CheckResponder(identity, status, writer, notifications, timeoutMs = 10_000L)

        var result: Result<Unit>? = null
        val job = launch {
            result = responder.respond("check1")
        }
        runCurrent()
        advanceTimeBy(10_001L)
        runCurrent()

        assertTrue(result?.isFailure == true)
        job.cancel()
    }

    @Test
    fun noticeFailureStillWritesReply() = runTest {
        val identity = FakeDeviceIdentity("dev1")
        val status = FakeDeviceStatusSource(accessibility = false, notifications = true)
        val writer = FakeDeviceStatusWriter()
        val notifications = FakeAgentNotifications().apply { shouldThrowAccessibilityOff = true }
        val responder = CheckResponder(identity, status, writer, notifications)

        val result = responder.respond("check1")

        assertTrue(result.isSuccess)
        assertEquals(1, notifications.showAccessibilityOffCalls)
        assertEquals(1, writer.replies.size)
        assertEquals("dev1", writer.replies.first().deviceId)
        assertEquals("check1", writer.replies.first().checkId)
        assertFalse(writer.replies.first().accessibilityOn)
    }
}
