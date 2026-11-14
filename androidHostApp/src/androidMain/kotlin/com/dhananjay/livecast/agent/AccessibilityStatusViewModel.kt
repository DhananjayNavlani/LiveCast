package com.dhananjay.livecast.agent

import android.content.Context
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface InstallSourceProvider {
    val sdkInt: Int
    fun isFromStore(): Boolean
}

class AndroidInstallSourceProvider(
    private val context: Context,
    override val sdkInt: Int = Build.VERSION.SDK_INT
) : InstallSourceProvider {
    override fun isFromStore(): Boolean {
        if (sdkInt < Build.VERSION_CODES.TIRAMISU) {
            return false
        }
        return try {
            val info = context.packageManager.getInstallSourceInfo(context.packageName)
            info.packageSource == PackageInstaller.PACKAGE_SOURCE_STORE
        } catch (_: Throwable) {
            false
        }
    }
}

data class AccessibilityStatusUiState(
    val accessibilityOn: Boolean,
    val showRestrictedStep: Boolean
)

class AccessibilityStatusViewModel(
    private val status: DeviceStatusSource,
    private val install: InstallSourceProvider
) : ViewModel() {

    private var skippedRestricted: Boolean = false
    private val _state = MutableStateFlow(computeState())
    val state: StateFlow<AccessibilityStatusUiState> = _state.asStateFlow()

    private fun computeState(): AccessibilityStatusUiState {
        val on = status.accessibilityOn()
        if (on) {
            skippedRestricted = false
        }
        val showRestricted = !on && install.sdkInt >= 33 && !install.isFromStore() && !skippedRestricted
        return AccessibilityStatusUiState(
            accessibilityOn = on,
            showRestrictedStep = showRestricted
        )
    }

    fun refresh() {
        _state.value = computeState()
    }

    fun skipRestrictedStep() {
        skippedRestricted = true
        _state.value = _state.value.copy(showRestrictedStep = false)
    }
}
