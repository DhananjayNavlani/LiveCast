package com.dhananjay.livecast.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhananjay.livecast.protocol.GroupRules
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class SupervisedByUiState(
    val enrolled: Boolean? = null,
    val supervisorNames: List<String> = emptyList(),
    val error: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class SupervisedByViewModel(
    identity: DeviceIdentity,
    registry: DeviceRegistry,
    members: GroupMembersDirectory
) : ViewModel() {

    val state: StateFlow<SupervisedByUiState> = flow {
        val deviceId = identity.deviceId
        if (deviceId == null) {
            emit(SupervisedByUiState(enrolled = false))
            return@flow
        }
        var lastEnrolled: Boolean? = null
        val flow: Flow<SupervisedByUiState> = registry.observeDevice(deviceId)
            .map { device ->
                val groupId = device?.groupId
                if (groupId != null) {
                    lastEnrolled = true
                } else {
                    lastEnrolled = false
                }
                groupId
            }
            .distinctUntilChanged()
            .flatMapLatest { groupId ->
                if (groupId == null) {
                    flowOf(SupervisedByUiState(enrolled = false))
                } else {
                    members.observeSupervisors(groupId)
                        .map { supervisors ->
                            SupervisedByUiState(
                                enrolled = true,
                                supervisorNames = supervisors.map { GroupRules.displayName(it) },
                                error = null
                            )
                        }
                        .catch { e ->
                            emit(
                                SupervisedByUiState(
                                    enrolled = true,
                                    supervisorNames = emptyList(),
                                    error = e.message ?: "Failed to observe supervisors"
                                )
                            )
                        }
                }
            }
            .catch { e ->
                emit(
                    SupervisedByUiState(
                        enrolled = lastEnrolled,
                        supervisorNames = emptyList(),
                        error = e.message ?: "Failed to observe device"
                    )
                )
            }
        emitAll(flow)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = SupervisedByUiState(enrolled = null)
    )
}
