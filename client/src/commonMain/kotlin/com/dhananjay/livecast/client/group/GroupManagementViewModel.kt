package com.dhananjay.livecast.client.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhananjay.livecast.auth.AuthState
import com.dhananjay.livecast.auth.SessionProvider
import com.dhananjay.livecast.protocol.GroupMember
import com.dhananjay.livecast.protocol.GroupMembership
import com.dhananjay.livecast.protocol.GroupRole
import com.dhananjay.livecast.protocol.GroupRules
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GroupManagementUiState(
    val memberships: List<GroupMembership> = emptyList(),
    val selectedGroupId: String? = null,
    val members: List<GroupMember> = emptyList(),
    val canManage: Boolean = false,
    val myEmail: String? = null,
    val busy: Boolean = false,
    val error: String? = null,
)

class GroupManagementViewModel(
    private val session: SessionProvider,
    private val groups: GroupRepository,
    private val members: GroupMemberRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(GroupManagementUiState())
    val state: StateFlow<GroupManagementUiState> = _state.asStateFlow()

    private var membershipsJob: Job? = null
    private var membersJob: Job? = null

    init {
        viewModelScope.launch {
            session.authState.collect { authState ->
                rebind(authState)
            }
        }
    }

    private fun rebind(authState: AuthState) {
        membershipsJob?.cancel()
        membersJob?.cancel()

        val user = (authState as? AuthState.Authenticated)?.user
        val myEmail = user?.email?.let { GroupRules.normalizeEmail(it) }

        _state.value = GroupManagementUiState(myEmail = myEmail)

        if (user == null || user.isAnonymous || user.email.isNullOrBlank()) {
            return
        }

        membershipsJob = viewModelScope.launch {
            groups.observeMyMemberships()
                .catch { e ->
                    if (e is CancellationException) throw e
                    handleError(e)
                }
                .collect { memberships ->
                    onMembershipsUpdated(memberships)
                }
        }
    }

    private fun onMembershipsUpdated(memberships: List<GroupMembership>) {
        val currentSelectedId = _state.value.selectedGroupId
        val newSelectedId = if (currentSelectedId != null && memberships.any { it.group.id == currentSelectedId }) {
            currentSelectedId
        } else {
            memberships.firstOrNull()?.group?.id
        }

        val selectedMembership = memberships.firstOrNull { it.group.id == newSelectedId }
        val canManage = selectedMembership?.myRole == GroupRole.OWNER

        _state.update {
            it.copy(
                memberships = memberships,
                selectedGroupId = newSelectedId,
                canManage = canManage,
            )
        }

        if (newSelectedId != null) {
            observeMembers(newSelectedId)
        } else {
            membersJob?.cancel()
            _state.update { it.copy(members = emptyList()) }
        }
    }

    fun selectGroup(groupId: String) {
        val membership = _state.value.memberships.firstOrNull { it.group.id == groupId } ?: return
        _state.update {
            it.copy(
                selectedGroupId = groupId,
                canManage = membership.myRole == GroupRole.OWNER,
                error = null,
            )
        }
        observeMembers(groupId)
    }

    private fun observeMembers(groupId: String) {
        membersJob?.cancel()
        membersJob = viewModelScope.launch {
            members.observeMembers(groupId)
                .catch { e ->
                    if (e is CancellationException) throw e
                    handleError(e)
                }
                .collect { memberList ->
                    _state.update { it.copy(members = memberList, error = null) }
                }
        }
    }

    fun createGroup(name: String) {
        if (_state.value.busy) return
        val normalizedName = GroupRules.normalizeGroupName(name)
        if (normalizedName == null) {
            _state.update { it.copy(error = "Enter a group name (up to 60 characters)") }
            return
        }

        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val result = groups.createGroup(normalizedName)
            _state.update { current ->
                result.fold(
                    onSuccess = { newGroupId ->
                        current.copy(busy = false, selectedGroupId = newGroupId)
                    },
                    onFailure = { e ->
                        current.copy(busy = false, error = e.message ?: "Failed to create group")
                    }
                )
            }
        }
    }

    fun addMember(email: String, role: GroupRole, name: String?) {
        if (_state.value.busy) return
        if (!_state.value.canManage) return

        val normalizedEmail = GroupRules.normalizeEmail(email)
        if (!GroupRules.isValidEmail(normalizedEmail)) {
            _state.update { it.copy(error = "Enter a valid email") }
            return
        }

        val groupId = _state.value.selectedGroupId ?: return

        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val result = members.addMember(groupId, normalizedEmail, role, name?.takeIf { it.isNotBlank() })
            _state.update { current ->
                result.fold(
                    onSuccess = { current.copy(busy = false) },
                    onFailure = { e -> current.copy(busy = false, error = e.message ?: "Failed to add member") }
                )
            }
        }
    }

    fun changeRole(email: String, role: GroupRole) {
        if (!_state.value.canManage) return

        val normalizedEmail = GroupRules.normalizeEmail(email)
        val myNormEmail = _state.value.myEmail?.let { GroupRules.normalizeEmail(it) }
        if (normalizedEmail == myNormEmail) {
            _state.update { it.copy(error = "You can't remove or demote yourself") }
            return
        }

        val groupId = _state.value.selectedGroupId ?: return

        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val result = members.changeRole(groupId, normalizedEmail, role)
            _state.update { current ->
                result.fold(
                    onSuccess = { current.copy(busy = false) },
                    onFailure = { e -> current.copy(busy = false, error = e.message ?: "Failed to change role") }
                )
            }
        }
    }

    fun removeMember(email: String) {
        if (!_state.value.canManage) return

        val normalizedEmail = GroupRules.normalizeEmail(email)
        val myNormEmail = _state.value.myEmail?.let { GroupRules.normalizeEmail(it) }
        if (normalizedEmail == myNormEmail) {
            _state.update { it.copy(error = "You can't remove or demote yourself") }
            return
        }

        val groupId = _state.value.selectedGroupId ?: return

        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val result = members.removeMember(groupId, normalizedEmail)
            _state.update { current ->
                result.fold(
                    onSuccess = { current.copy(busy = false) },
                    onFailure = { e -> current.copy(busy = false, error = e.message ?: "Failed to remove member") }
                )
            }
        }
    }

    private fun handleError(e: Throwable) {
        if (e is GroupError.AccessLost) {
            _state.update { it.copy(error = "You no longer have access", members = emptyList()) }
        } else {
            _state.update { it.copy(error = e.message ?: "Error occurred") }
        }
    }
}
