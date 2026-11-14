package com.dhananjay.livecast.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhananjay.livecast.agent.EnrollmentListener
import com.dhananjay.livecast.auth.AuthResult
import com.dhananjay.livecast.auth.AuthState
import com.dhananjay.livecast.auth.EmailVerifier
import com.dhananjay.livecast.auth.SessionProvider
import com.dhananjay.livecast.auth.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
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
    private val registry: DeviceRegistry,
    private val listeners: List<EnrollmentListener> = emptyList()
) : ViewModel() {

    private val _state = MutableStateFlow<EnrollmentUiState>(EnrollmentUiState.Loading)
    val state: StateFlow<EnrollmentUiState> = _state.asStateFlow()

    private var lastCandidates: List<EnrollmentCandidate> = emptyList()

    private var authScope: CoroutineScope? = null
    private var deviceJob: Job? = null
    private var lookupJob: Job? = null

    init {
        viewModelScope.launch {
            session.authState.collectLatest { authState ->
                when (authState) {
                    is AuthState.Authenticated -> {
                        coroutineScope {
                            authScope = this
                            try {
                                startDeviceObservation(authState.user, this)
                                awaitCancellation()
                            } finally {
                                if (authScope === this) {
                                    authScope = null
                                    deviceJob = null
                                    lookupJob = null
                                }
                            }
                        }
                    }
                    AuthState.Loading -> {
                        cancelAuthJobs()
                        _state.value = EnrollmentUiState.Loading
                    }
                    is AuthState.Error -> {
                        cancelAuthJobs()
                        _state.value = EnrollmentUiState.Error(authState.message)
                    }
                    AuthState.Unauthenticated -> {
                        cancelAuthJobs()
                        _state.value = EnrollmentUiState.NoEmail
                    }
                }
            }
        }
    }

    private fun cancelAuthJobs() {
        authScope = null
        deviceJob?.cancel()
        deviceJob = null
        lookupJob?.cancel()
        lookupJob = null
    }

    private fun startDeviceObservation(user: User, scope: CoroutineScope) {
        deviceJob?.cancel()
        deviceJob = scope.launch {
            val email = user.email
            registry.observeDevice(user.id)
                .catch { e ->
                    _state.value = EnrollmentUiState.Error(e.message ?: "Failed to observe device")
                }
                .collectLatest { device ->
                    val groupId = device?.groupId
                    if (groupId != null) {
                        lookupJob?.cancel()
                        _state.value = EnrollmentUiState.Enrolled(groupId)
                    } else {
                        if (user.isAnonymous || email.isNullOrBlank()) {
                            lookupJob?.cancel()
                            _state.value = EnrollmentUiState.NoEmail
                        } else if (!user.emailVerified) {
                            lookupJob?.cancel()
                            _state.value = EnrollmentUiState.VerifyEmail(email = email)
                        } else {
                            startLookup(email, scope)
                        }
                    }
                }
        }
    }

    private fun startLookup(email: String, scope: CoroutineScope) {
        lookupJob?.cancel()
        lookupJob = scope.launch {
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
                        if (!verified) {
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
        val scope = authScope ?: return
        val email = user.email
        if (deviceJob?.isActive != true) {
            startDeviceObservation(user, scope)
        } else if (!email.isNullOrBlank()) {
            startLookup(email, scope)
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
                        listeners.forEach { listener ->
                            try {
                                listener.onEnrolled(device.id, current.candidate.group.id)
                            } catch (_: Exception) {
                                // listener failure is logged and never fails enrollment
                            }
                        }
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
