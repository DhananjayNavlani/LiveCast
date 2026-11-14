package com.dhananjay.livecast.agent

import com.dhananjay.livecast.group.DeviceIdentity
import com.dhananjay.livecast.protocol.AppEventType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AppChangeMonitorTest {

    private class FakeDeviceIdentity(override var deviceId: String? = "dev1") : DeviceIdentity

    private class FakeEnrollmentState(
        var enrollmentStatus: EnrollmentStatus = EnrollmentStatus.Enrolled("grp1")
    ) : EnrollmentState {
        override suspend fun status(): EnrollmentStatus = enrollmentStatus
    }

    private class FakeInstalledAppsSource(
        var apps: List<InstalledApp> = emptyList()
    ) : InstalledAppsSource {
        override fun userApps(): List<InstalledApp> = apps
        override fun app(packageName: String): InstalledApp? =
            apps.firstOrNull { it.packageName == packageName }
    }

    private class FakeAppBaselineStore(
        var baseline: Map<String, String>? = null
    ) : AppBaselineStore {
        var clearCalls = 0
        val saveCalls = mutableListOf<Map<String, String>>()

        override suspend fun load(): Map<String, String>? = baseline

        override suspend fun save(baseline: Map<String, String>) {
            this.baseline = baseline
            saveCalls.add(baseline)
        }

        override suspend fun clear() {
            baseline = null
            clearCalls++
        }
    }

    private class FakeAppEventWriter : AppEventWriter {
        val events = mutableListOf<Pair<String, NewAppEvent>>()

        override fun write(deviceId: String, event: NewAppEvent) {
            events.add(deviceId to event)
        }
    }

    private class FakePackageChangeRegistrar : PackageChangeRegistrar {
        var callback: ((PackageChange) -> Unit)? = null
        var unregisterCalls = 0

        override fun register(onChange: (PackageChange) -> Unit) {
            callback = onChange
        }

        override fun unregister() {
            callback = null
            unregisterCalls++
        }
    }

    private class FakeWallClock(var now: Long = 10_000L) : WallClock {
        override fun nowMs(): Long = now
    }

    @Test
    fun notEnrolledDoesNothing() = runTest {
        val identity = FakeDeviceIdentity("dev1")
        val enrollment = FakeEnrollmentState(EnrollmentStatus.NotEnrolled)
        val apps = FakeInstalledAppsSource(listOf(InstalledApp("com.a", "A", 1000L)))
        val baseline = FakeAppBaselineStore(mapOf("com.a" to "A"))
        val writer = FakeAppEventWriter()
        val registrar = FakePackageChangeRegistrar()
        val clock = FakeWallClock()

        val monitor = AppChangeMonitor(identity, enrollment, apps, baseline, writer, registrar, clock)
        monitor.start(this)
        advanceUntilIdle()

        assertEquals(0, baseline.clearCalls)
        assertEquals(mapOf("com.a" to "A"), baseline.baseline)
        assertNull(registrar.callback)
        assertTrue(writer.events.isEmpty())
        monitor.stop()
    }

    @Test
    fun startWritesDiffAndSavesBaseline() = runTest {
        val identity = FakeDeviceIdentity("dev1")
        val enrollment = FakeEnrollmentState(EnrollmentStatus.Enrolled("grp1"))
        val apps = FakeInstalledAppsSource(
            listOf(
                InstalledApp("com.a", "A", 1000L),
                InstalledApp("com.b", "B", 1500L)
            )
        )
        val baseline = FakeAppBaselineStore(mapOf("com.a" to "A"))
        val writer = FakeAppEventWriter()
        val registrar = FakePackageChangeRegistrar()
        val clock = FakeWallClock(2000L)

        val monitor = AppChangeMonitor(identity, enrollment, apps, baseline, writer, registrar, clock)
        monitor.start(this)
        advanceUntilIdle()

        assertEquals(1, writer.events.size)
        val (deviceId, event) = writer.events.first()
        assertEquals("dev1", deviceId)
        assertEquals(AppEventType.INSTALLED, event.type)
        assertEquals("com.b", event.packageName)
        assertEquals("B", event.label)
        assertEquals(1500L, event.atEpochMs)
        assertEquals(mapOf("com.a" to "A", "com.b" to "B"), baseline.baseline)
        monitor.stop()
    }

    @Test
    fun startRegistersReceiver() = runTest {
        val identity = FakeDeviceIdentity("dev1")
        val enrollment = FakeEnrollmentState(EnrollmentStatus.Enrolled("grp1"))
        val apps = FakeInstalledAppsSource()
        val baseline = FakeAppBaselineStore()
        val writer = FakeAppEventWriter()
        val registrar = FakePackageChangeRegistrar()
        val clock = FakeWallClock()

        val monitor = AppChangeMonitor(identity, enrollment, apps, baseline, writer, registrar, clock)
        monitor.start(this)
        advanceUntilIdle()

        assertNotNull(registrar.callback)
        monitor.stop()
    }

    @Test
    fun broadcastWritesAndUpdatesBaseline() = runTest {
        val identity = FakeDeviceIdentity("dev1")
        val enrollment = FakeEnrollmentState(EnrollmentStatus.Enrolled("grp1"))
        val apps = FakeInstalledAppsSource(listOf(InstalledApp("com.a", "A", 1000L)))
        val baseline = FakeAppBaselineStore(mapOf("com.a" to "A"))
        val writer = FakeAppEventWriter()
        val registrar = FakePackageChangeRegistrar()
        val clock = FakeWallClock()

        val monitor = AppChangeMonitor(identity, enrollment, apps, baseline, writer, registrar, clock)
        monitor.start(this)
        advanceUntilIdle()

        // New app installed and broadcast fires
        apps.apps = listOf(
            InstalledApp("com.a", "A", 1000L),
            InstalledApp("com.b", "B", 2500L)
        )
        val change = PackageChange("com.b", added = true, replacing = false, atEpochMs = 2500L)
        registrar.callback!!.invoke(change)
        advanceUntilIdle()

        assertEquals(1, writer.events.size)
        val (deviceId, event) = writer.events.first()
        assertEquals("dev1", deviceId)
        assertEquals(AppEventType.INSTALLED, event.type)
        assertEquals("com.b", event.packageName)
        assertEquals("B", event.label)
        assertEquals(2500L, event.atEpochMs)
        assertEquals(mapOf("com.a" to "A", "com.b" to "B"), baseline.baseline)
        monitor.stop()
    }

    @Test
    fun stopUnregisters() = runTest {
        val identity = FakeDeviceIdentity("dev1")
        val enrollment = FakeEnrollmentState(EnrollmentStatus.Enrolled("grp1"))
        val apps = FakeInstalledAppsSource()
        val baseline = FakeAppBaselineStore()
        val writer = FakeAppEventWriter()
        val registrar = FakePackageChangeRegistrar()
        val clock = FakeWallClock()

        val monitor = AppChangeMonitor(identity, enrollment, apps, baseline, writer, registrar, clock)
        monitor.start(this)
        advanceUntilIdle()

        monitor.stop()
        advanceUntilIdle()

        assertEquals(1, registrar.unregisterCalls)
    }

    @Test
    fun broadcastAfterStopIgnored() = runTest {
        val identity = FakeDeviceIdentity("dev1")
        val enrollment = FakeEnrollmentState(EnrollmentStatus.Enrolled("grp1"))
        val apps = FakeInstalledAppsSource(listOf(InstalledApp("com.a", "A", 1000L)))
        val baseline = FakeAppBaselineStore(mapOf("com.a" to "A"))
        val writer = FakeAppEventWriter()
        val registrar = FakePackageChangeRegistrar()
        val clock = FakeWallClock()

        val monitor = AppChangeMonitor(identity, enrollment, apps, baseline, writer, registrar, clock)
        monitor.start(this)
        advanceUntilIdle()

        val capturedCallback = registrar.callback
        assertNotNull(capturedCallback)

        monitor.stop()
        advanceUntilIdle()

        apps.apps = listOf(
            InstalledApp("com.a", "A", 1000L),
            InstalledApp("com.b", "B", 2500L)
        )
        capturedCallback.invoke(PackageChange("com.b", added = true, replacing = false, atEpochMs = 2500L))
        advanceUntilIdle()

        assertTrue(writer.events.isEmpty())
    }

    @Test
    fun unknownThenEnrolledStartsAfterRetry() = runTest {
        val identity = FakeDeviceIdentity("dev1")
        var attempts = 0
        val enrollment = object : EnrollmentState {
            override suspend fun status(): EnrollmentStatus {
                attempts++
                return if (attempts == 1) EnrollmentStatus.Unknown else EnrollmentStatus.Enrolled("grp1")
            }
        }
        val apps = FakeInstalledAppsSource(
            listOf(
                InstalledApp("com.a", "A", 1000L),
                InstalledApp("com.b", "B", 1500L)
            )
        )
        val baseline = FakeAppBaselineStore(mapOf("com.a" to "A"))
        val writer = FakeAppEventWriter()
        val registrar = FakePackageChangeRegistrar()
        val clock = FakeWallClock(2000L)

        val monitor = AppChangeMonitor(identity, enrollment, apps, baseline, writer, registrar, clock)
        monitor.start(this)
        advanceUntilIdle()

        assertEquals(1, writer.events.size)
        val (deviceId, event) = writer.events.first()
        assertEquals("dev1", deviceId)
        assertEquals("com.b", event.packageName)
        assertNotNull(registrar.callback)
        monitor.stop()
    }
}
