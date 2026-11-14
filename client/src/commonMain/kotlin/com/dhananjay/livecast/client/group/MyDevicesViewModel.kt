@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.dhananjay.livecast.client.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhananjay.livecast.auth.AuthState
import com.dhananjay.livecast.auth.SessionProvider
import com.dhananjay.livecast.client.check.CheckRequestResult
import com.dhananjay.livecast.client.check.CheckState
import com.dhananjay.livecast.client.check.DeviceCheckService
import com.dhananjay.livecast.client.check.awaitCheckReply
import com.dhananjay.livecast.protocol.CheckRules
import com.dhananjay.livecast.protocol.GroupMembership
import com.dhananjay.livecast.protocol.GroupRole
import com.dhananjay.livecast.protocol.GroupRules
import com.dhananjay.livecast.protocol.HostDevice
import com.dhananjay.livecast.protocol.ToggleState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock

data class DeviceRow(
    val device: HostDevice,
    val groupName: String,
    val accessibility: ToggleState,
    val notifications: ToggleState,
    val statusAtEpochMs: Long?,
    val check: CheckState,
    val canRemove: Boolean,
)

data class MyDevicesUiState(
    val rows: List<DeviceRow> = emptyList(),
    val guest: Boolean = false,
    val hasGroups: Boolean = false,
    val error: String? = null,
)

class MyDevicesViewModel(
    private val session: SessionProvider,
    private val groups: GroupRepository,
    private val devices: GroupDeviceRepository,
    private val checks: DeviceCheckService,
) : ViewModel() {

    private val _state = MutableStateFlow(MyDevicesUiState())
    val state: StateFlow<MyDevicesUiState> = _state.asStateFlow()

    private var activeJob: Job? = null
    private var devicesJob: Job? = null
    private val checkJobs = mutableMapOf<String, Job>()
    private var rebindGeneration = 0

    private val _devicesFlow = MutableStateFlow<List<HostDevice>>(emptyList())
    private var currentMemberships: List<GroupMembership> = emptyList()
    private var currentViewableGroupIds: List<String>? = null
    private val checkStates = mutableMapOf<String, CheckState>()

    init {
        viewModelScope.launch {
            session.authState.collect { authState ->
                rebind(authState)
            }
        }
    }

    private fun rebind(authState: AuthState) {
        rebindGeneration++
        activeJob?.cancel()
        devicesJob?.cancel()
        checkJobs.values.forEach { it.cancel() }
        checkJobs.clear()
        checkStates.clear()
        _devicesFlow.value = emptyList()
        currentMemberships = emptyList()
        currentViewableGroupIds = null

        val user = (authState as? AuthState.Authenticated)?.user
        when (clientGate(user)) {
            ClientGate.SIGN_IN, ClientGate.GUEST -> {
                _state.value = MyDevicesUiState(guest = true)
                return
            }
            ClientGate.VERIFY_EMAIL -> {
                _state.value = MyDevicesUiState(guest = false)
                return
            }
            ClientGate.READY -> {
                _state.value = MyDevicesUiState(guest = false)
            }
        }

        activeJob = viewModelScope.launch {
            groups.observeMyMemberships()
                .catch { e ->
                    if (e is CancellationException) throw e
                    handleError(e)
                }
                .collect { memberships ->
                    currentMemberships = memberships
                    val hasGroups = memberships.isNotEmpty()
                    val viewableIds = GroupRules.viewableGroupIds(memberships)

                    _state.update { it.copy(hasGroups = hasGroups) }

                    if (viewableIds != currentViewableGroupIds) {
                        currentViewableGroupIds = viewableIds
                        observeDevicesForGroups(viewableIds)
                    } else {
                        updateRows()
                    }
                }
        }
    }

    private fun observeDevicesForGroups(viewableGroupIds: List<String>) {
        devicesJob?.cancel()
        if (viewableGroupIds.isEmpty()) {
            _devicesFlow.value = emptyList()
            updateRows()
            return
        }

        devicesJob = viewModelScope.launch {
            devices.observeDevices(viewableGroupIds)
                .catch { e ->
                    if (e is CancellationException) throw e
                    handleError(e)
                }
                .collect { deviceList ->
                    _devicesFlow.value = deviceList
                    _state.update { it.copy(error = null) }
                    updateRows()
                }
        }
    }

    private fun updateRows() {
        val groupMap = currentMemberships.associateBy { it.group.id }
        val rows = _devicesFlow.value.map { device ->
            val membership = device.groupId?.let { groupMap[it] }
            val groupName = membership?.group?.name ?: ""
            val canRemove = membership?.myRole == GroupRole.OWNER
            val check = checkStates[device.id] ?: CheckState.Idle
            DeviceRow(
                device = device,
                groupName = groupName,
                accessibility = device.accessibility,
                notifications = device.notifications,
                statusAtEpochMs = device.statusAtEpochMs,
                check = check,
                canRemove = canRemove,
            )
        }
        _state.update { it.copy(rows = rows) }
    }

    fun check(deviceId: String) {
        if (checkStates[deviceId] == CheckState.Checking) return
        checkStates[deviceId] = CheckState.Checking
        updateRows()

        val generation = rebindGeneration
        checkJobs[deviceId]?.cancel()
        checkJobs[deviceId] = viewModelScope.launch {
            try {
                val outcome = checks.requestCheck(deviceId)
                when (outcome) {
                    is CheckRequestResult.Requested -> {
                        val replyAt = awaitCheckReply(
                            devices = _devicesFlow,
                            deviceId = deviceId,
                            checkId = outcome.checkId,
                            timeoutMs = CheckRules.REPLY_TIMEOUT_MS,
                            nowMs = { Clock.System.now().toEpochMilliseconds() }
                        )
                        checkStates[deviceId] = if (replyAt != null) {
                            CheckState.Online(replyAt)
                        } else {
                            CheckState.Offline
                        }
                        updateRows()
                    }
                    is CheckRequestResult.AppRemoved -> {
                        checkStates[deviceId] = CheckState.AppRemoved
                        updateRows()
                    }
                    is CheckRequestResult.NoPushToken -> {
                        checkStates[deviceId] = CheckState.NotSetUp
                        updateRows()
                    }
                    is CheckRequestResult.NotAllowed -> {
                        checkStates[deviceId] = CheckState.Error("You no longer have access")
                        updateRows()
                    }
                    is CheckRequestResult.RateLimited -> {
                        checkStates[deviceId] = CheckState.RateLimited
                        updateRows()
                    }
                    is CheckRequestResult.Failed -> {
                        checkStates[deviceId] = CheckState.Error(outcome.message)
                        updateRows()
                    }
                }
            } finally {
                if (generation == rebindGeneration && checkStates[deviceId] == CheckState.Checking) {
                    checkStates[deviceId] = CheckState.Error("Check failed")
                    updateRows()
                }
            }
        }
    }

    private fun handleError(e: Throwable) {
        if (e is GroupError.AccessLost) {
            _devicesFlow.value = emptyList()
            _state.update { it.copy(rows = emptyList(), error = "You no longer have access") }
        } else {
            _state.update { it.copy(error = e.message ?: "Failed to load devices") }
        }
    }

    fun remove(deviceId: String) {
        val row = _state.value.rows.firstOrNull { it.device.id == deviceId }
        if (row == null || !row.canRemove) return

        viewModelScope.launch {
            val result = devices.removeFromGroup(deviceId)
            result.onFailure { e ->
                _state.update { it.copy(error = e.message ?: "Failed to remove device") }
            }
        }
    }
}
