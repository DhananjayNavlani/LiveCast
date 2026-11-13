package com.dhananjay.livecast.client.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhananjay.livecast.auth.AuthState
import com.dhananjay.livecast.auth.SessionProvider
import com.dhananjay.livecast.protocol.GroupMembership
import com.dhananjay.livecast.protocol.GroupRole
import com.dhananjay.livecast.protocol.GroupRules
import com.dhananjay.livecast.protocol.HostDevice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DeviceRow(
    val device: HostDevice,
    val groupName: String,
    val online: Boolean,
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
    private val clock: PresenceClock,
    private val tickMs: Long = GroupRules.PRESENCE_TICK_MS,
) : ViewModel() {

    private val _state = MutableStateFlow(MyDevicesUiState())
    val state: StateFlow<MyDevicesUiState> = _state.asStateFlow()

    private var activeJob: Job? = null
    private var devicesJob: Job? = null
    private var tickerJob: Job? = null

    private var currentDevices: List<HostDevice> = emptyList()
    private var currentMemberships: List<GroupMembership> = emptyList()

    init {
        viewModelScope.launch {
            session.authState.collect { authState ->
                rebind(authState)
            }
        }
    }

    private fun rebind(authState: AuthState) {
        activeJob?.cancel()
        devicesJob?.cancel()
        tickerJob?.cancel()
        currentDevices = emptyList()
        currentMemberships = emptyList()

        val user = (authState as? AuthState.Authenticated)?.user
        val isGuest = user == null || user.isAnonymous || user.email.isNullOrBlank()

        if (isGuest) {
            _state.value = MyDevicesUiState(guest = true)
            return
        }

        _state.value = MyDevicesUiState(guest = false)

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

                    observeDevicesForGroups(viewableIds)
                }
        }

        startTicker()
    }

    private fun observeDevicesForGroups(viewableGroupIds: List<String>) {
        devicesJob?.cancel()
        if (viewableGroupIds.isEmpty()) {
            currentDevices = emptyList()
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
                    currentDevices = deviceList
                    _state.update { it.copy(error = null) }
                    updateRows()
                }
        }
    }

    private fun startTicker() {
        tickerJob = viewModelScope.launch {
            clock.ticks(tickMs).collect {
                updateRows()
            }
        }
    }

    private fun updateRows() {
        val now = clock.nowEpochMs()
        val groupMap = currentMemberships.associateBy { it.group.id }
        val rows = currentDevices.map { device ->
            val membership = device.groupId?.let { groupMap[it] }
            val groupName = membership?.group?.name ?: ""
            val canRemove = membership?.myRole == GroupRole.OWNER
            val online = GroupRules.isOnline(device, now)
            DeviceRow(
                device = device,
                groupName = groupName,
                online = online,
                canRemove = canRemove,
            )
        }
        _state.update { it.copy(rows = rows) }
    }

    private fun handleError(e: Throwable) {
        if (e is GroupError.AccessLost) {
            currentDevices = emptyList()
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
