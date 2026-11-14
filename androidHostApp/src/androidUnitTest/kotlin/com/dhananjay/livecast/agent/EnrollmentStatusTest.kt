package com.dhananjay.livecast.agent

import com.dhananjay.livecast.group.DeviceIdentity
import com.dhananjay.livecast.group.DeviceRegistry
import com.dhananjay.livecast.protocol.HostDevice
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class EnrollmentStatusTest {

    private class FakeDeviceIdentity(override var deviceId: String? = "dev1") : DeviceIdentity

    private class FakeDeviceRegistry(
        var deviceFlow: Flow<HostDevice?> = flowOf(null)
    ) : DeviceRegistry {
        override fun observeDevice(deviceId: String): Flow<HostDevice?> = deviceFlow
        override suspend fun enroll(device: HostDevice, groupId: String): Result<Unit> = Result.success(Unit)
    }

    @Test
    fun timeoutIsUnknown() = runTest {
        val identity = FakeDeviceIdentity("dev1")
        val registry = FakeDeviceRegistry(flow { awaitCancellation() })
        val enrollment = DeviceRegistryEnrollmentState(identity, registry)

        val status = enrollment.status()
        assertEquals(EnrollmentStatus.Unknown, status)
    }

    @Test
    fun exceptionIsUnknown() = runTest {
        val identity = FakeDeviceIdentity("dev1")
        val registry = FakeDeviceRegistry(flow { throw RuntimeException("Firestore network error") })
        val enrollment = DeviceRegistryEnrollmentState(identity, registry)

        val status = enrollment.status()
        assertEquals(EnrollmentStatus.Unknown, status)
    }

    @Test
    fun nullGroupIsNotEnrolled() = runTest {
        val identity = FakeDeviceIdentity("dev1")
        val device = HostDevice(
            id = "dev1",
            displayName = "Pixel",
            model = "Pixel 8",
            groupId = null,
            enrolledAtEpochMs = 1000L
        )
        val registry = FakeDeviceRegistry(flowOf(device))
        val enrollment = DeviceRegistryEnrollmentState(identity, registry)

        val status = enrollment.status()
        assertEquals(EnrollmentStatus.NotEnrolled, status)
    }

    @Test
    fun awaitStatusRetriesUnknownThenEnrolled() = runTest {
        var attempts = 0
        val state = object : EnrollmentState {
            override suspend fun status(): EnrollmentStatus {
                attempts++
                return if (attempts == 1) EnrollmentStatus.Unknown else EnrollmentStatus.Enrolled("grp1")
            }
        }

        val result = state.awaitStatus(listOf(15_000L))
        assertIs<EnrollmentStatus.Enrolled>(result)
        assertEquals("grp1", result.groupId)
        assertEquals(2, attempts)
    }

    @Test
    fun awaitStatusGivesUpAfterDelays() = runTest {
        var attempts = 0
        val state = object : EnrollmentState {
            override suspend fun status(): EnrollmentStatus {
                attempts++
                return EnrollmentStatus.Unknown
            }
        }

        val result = state.awaitStatus(listOf(15_000L, 30_000L))
        assertEquals(EnrollmentStatus.Unknown, result)
        assertEquals(3, attempts)
    }
}
