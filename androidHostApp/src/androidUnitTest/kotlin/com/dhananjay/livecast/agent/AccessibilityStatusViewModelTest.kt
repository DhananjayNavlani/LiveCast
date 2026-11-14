package com.dhananjay.livecast.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccessibilityStatusViewModelTest {

    private class FakeDeviceStatusSource(
        var accessibility: Boolean = false,
        var notifications: Boolean = true
    ) : DeviceStatusSource {
        override fun accessibilityOn(): Boolean = accessibility
        override fun notificationsOn(): Boolean = notifications
    }

    private class FakeInstallSourceProvider(
        override val sdkInt: Int = 34,
        var store: Boolean = false
    ) : InstallSourceProvider {
        override fun isFromStore(): Boolean = store
    }

    @Test
    fun onShowsOn() {
        val status = FakeDeviceStatusSource(accessibility = true)
        val install = FakeInstallSourceProvider(sdkInt = 34, store = false)
        val vm = AccessibilityStatusViewModel(status, install)

        assertEquals(
            AccessibilityStatusUiState(accessibilityOn = true, showRestrictedStep = false),
            vm.state.value
        )
    }

    @Test
    fun offStoreInstallNoRestrictedStep() {
        val status = FakeDeviceStatusSource(accessibility = false)
        val install = FakeInstallSourceProvider(sdkInt = 33, store = true)
        val vm = AccessibilityStatusViewModel(status, install)

        assertEquals(
            AccessibilityStatusUiState(accessibilityOn = false, showRestrictedStep = false),
            vm.state.value
        )
    }

    @Test
    fun offSideloadedApi33ShowsRestrictedStep() {
        val status = FakeDeviceStatusSource(accessibility = false)
        val install = FakeInstallSourceProvider(sdkInt = 33, store = false)
        val vm = AccessibilityStatusViewModel(status, install)

        assertEquals(
            AccessibilityStatusUiState(accessibilityOn = false, showRestrictedStep = true),
            vm.state.value
        )
    }

    @Test
    fun offSideloadedApi32NoRestrictedStep() {
        val status = FakeDeviceStatusSource(accessibility = false)
        val install = FakeInstallSourceProvider(sdkInt = 32, store = false)
        val vm = AccessibilityStatusViewModel(status, install)

        assertEquals(
            AccessibilityStatusUiState(accessibilityOn = false, showRestrictedStep = false),
            vm.state.value
        )
    }

    @Test
    fun skipHidesRestrictedStep() {
        val status = FakeDeviceStatusSource(accessibility = false)
        val install = FakeInstallSourceProvider(sdkInt = 33, store = false)
        val vm = AccessibilityStatusViewModel(status, install)

        assertTrue(vm.state.value.showRestrictedStep)
        vm.skipRestrictedStep()
        assertFalse(vm.state.value.showRestrictedStep)

        // refresh while still off -> hidden
        vm.refresh()
        assertFalse(vm.state.value.showRestrictedStep)

        // then on -> refresh
        status.accessibility = true
        vm.refresh()
        assertFalse(vm.state.value.showRestrictedStep)

        // off -> refresh -> step shown again
        status.accessibility = false
        vm.refresh()
        assertTrue(vm.state.value.showRestrictedStep)
    }

    @Test
    fun refreshPicksUpChange() {
        val status = FakeDeviceStatusSource(accessibility = false)
        val install = FakeInstallSourceProvider(sdkInt = 33, store = true)
        val vm = AccessibilityStatusViewModel(status, install)

        assertFalse(vm.state.value.accessibilityOn)
        status.accessibility = true
        vm.refresh()
        assertTrue(vm.state.value.accessibilityOn)
    }
}
