package com.dhananjay.livecast.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhananjay.livecast.auth.AuthResult
import com.dhananjay.livecast.auth.AuthState
import com.dhananjay.livecast.auth.EmailVerifier
import com.dhananjay.livecast.auth.SessionProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

sealed interface EnrollmentUiState {
    data object Loading : EnrollmentUiState
    data object NoEmail : EnrollmentUiState
    data class VerifyEmail(
        val email: String,
        val sent: Boolean = false,
        val busy: Boolean = false,
        val message: String? = null
    ) : EnrollmentUiState
    data class NotMember(val email: String) : EnrollmentUiState
    data class ChooseGroup(val candidates: List<EnrollmentCandidate>) : EnrollmentUiState
    data class Confirm(
        val candidate: EnrollmentCandidate,
        val canGoBack: Boolean,
        val busy: Boolean = false,
        val error: String? = null
    ) : EnrollmentUiState
    data class Enrolled(val groupId: String) : EnrollmentUiState
    data class Error(val message: String) : EnrollmentUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
class EnrollmentViewModel(
    private val session: SessionProvider,
    private val verifier: EmailVerifier,
    private val deviceInfo: HostDeviceInfoProvider,
    private val directory: EnrollmentDirectory,
    private val registry: DeviceRegistry
) : ViewModel() {

    private val _state = MutableStateFlow<EnrollmentUiState>(EnrollmentUiState.Loading)
    val state: StateFlow<EnrollmentUiState> = _state.asStateFlow()

    private var lastCandidates: List<EnrollmentCandidate> = emptyList()

    init {
        viewModelScope.launch {
            session.authState.collectLatest { authState ->
                when (authState) {
                    is AuthState.Authenticated -> {
                        val user = authState.user
                        val email = user.email
                        registry.observeDevice(user.id).collectLatest { device ->
                            val groupId = device?.groupId
                            if (groupId != null) {
                                _state.value = EnrollmentUiState.Enrolled(groupId)
                            } else {
                                if (user.isAnonymous || email.isNullOrBlank()) {
                                    _state.value = EnrollmentUiState.NoEmail
                                } else if (!user.emailVerified) {
                                    _state.value = EnrollmentUiState.VerifyEmail(email = email)
                                } else {
                                    loadCandidatesInternal(email)
                                }
                            }
                        }
                    }
                    AuthState.Loading -> {
                        _state.value = EnrollmentUiState.Loading
                    }
                    is AuthState.Error -> {
                        _state.value = EnrollmentUiState.Error(authState.message)
                    }
                    AuthState.Unauthenticated -> {
                        _state.value = EnrollmentUiState.NoEmail
                    }
                }
            }
        }
    }

    private suspend fun loadCandidatesInternal(email: String) {
        _state.value = EnrollmentUiState.Loading
        val result = directory.candidates(email)
        result.fold(
            onSuccess = { candidates ->
                lastCandidates = candidates
                when (candidates.size) {
                    0 -> _state.value = EnrollmentUiState.NotMember(email)
                    1 -> _state.value = EnrollmentUiState.Confirm(candidate = candidates[0], canGoBack = false)
                    else -> _state.value = EnrollmentUiState.ChooseGroup(candidates = candidates)
                }
            },
            onFailure = { e ->
                _state.value = EnrollmentUiState.Error(e.message ?: "Failed to load groups")
            }
        )
    }

    fun sendVerification() {
        val current = _state.value
        if (current is EnrollmentUiState.VerifyEmail) {
            if (current.busy) return
            _state.value = current.copy(busy = true, message = null)
            viewModelScope.launch {
                val result = verifier.sendVerificationEmail()
                when (result) {
                    is AuthResult.Success -> {
                        val latest = _state.value
                        if (latest is EnrollmentUiState.VerifyEmail) {
                            _state.value = latest.copy(busy = false, sent = true)
                        }
                    }
                    is AuthResult.Error -> {
                        val latest = _state.value
                        if (latest is EnrollmentUiState.VerifyEmail) {
                            _state.value = latest.copy(busy = false, message = result.message)
                        }
                    }
                }
            }
        }
    }

    fun checkVerification() {
        val current = _state.value
        if (current is EnrollmentUiState.VerifyEmail) {
            if (current.busy) return
            _state.value = current.copy(busy = true, message = null)
            viewModelScope.launch {
                val result = verifier.refreshEmailVerification()
                result.fold(
                    onSuccess = { verified ->
                        if (verified) {
                            loadCandidatesInternal(current.email)
                        } else {
                            val latest = _state.value
                            if (latest is EnrollmentUiState.VerifyEmail) {
                                _state.value = latest.copy(
                                    busy = false,
                                    message = EnrollmentCopy.NOT_VERIFIED
                                )
                            }
                        }
                    },
                    onFailure = { e ->
                        val latest = _state.value
                        if (latest is EnrollmentUiState.VerifyEmail) {
                            _state.value = latest.copy(
                                busy = false,
                                message = e.message ?: "Failed to check verification"
                            )
                        }
                    }
                )
            }
        }
    }

    fun retry() {
        val user = (session.authState.value as? AuthState.Authenticated)?.user ?: return
        val email = user.email
        if (!email.isNullOrBlank()) {
            viewModelScope.launch {
                loadCandidatesInternal(email)
            }
        }
    }

    fun choose(groupId: String) {
        val candidate = lastCandidates.firstOrNull { it.group.id == groupId }
        if (candidate != null) {
            _state.value = EnrollmentUiState.Confirm(candidate = candidate, canGoBack = true)
        }
    }

    fun back() {
        val current = _state.value
        if (current is EnrollmentUiState.Confirm && current.canGoBack) {
            _state.value = EnrollmentUiState.ChooseGroup(lastCandidates)
        }
    }

    fun confirm() {
        val current = _state.value
        if (current is EnrollmentUiState.Confirm) {
            if (current.busy) return
            val user = (session.authState.value as? AuthState.Authenticated)?.user ?: return
            _state.value = current.copy(busy = true, error = null)
            viewModelScope.launch {
                val device = deviceInfo.current(user.id)
                val result = registry.enroll(device, current.candidate.group.id)
                result.fold(
                    onSuccess = {
                        _state.value = EnrollmentUiState.Enrolled(current.candidate.group.id)
                    },
                    onFailure = { e ->
                        val latest = _state.value
                        if (latest is EnrollmentUiState.Confirm) {
                            _state.value = latest.copy(
                                busy = false,
                                error = e.message ?: "Enrollment failed"
                            )
                        }
                    }
                )
            }
        }
    }
}
