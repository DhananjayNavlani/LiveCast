package com.dhananjay.livecast.client.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhananjay.livecast.auth.AuthResult
import com.dhananjay.livecast.auth.AuthState
import com.dhananjay.livecast.auth.EmailVerifier
import com.dhananjay.livecast.auth.SessionProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VerifyEmailUiState(
    val email: String? = null,
    val sent: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
)

class VerifyEmailViewModel(
    private val session: SessionProvider,
    private val verifier: EmailVerifier,
) : ViewModel() {

    private val _state = MutableStateFlow(
        VerifyEmailUiState(email = session.currentUser?.email)
    )
    val state: StateFlow<VerifyEmailUiState> = _state.asStateFlow()

    private var currentUid: String? = session.currentUser?.id
    private var inFlightJob: Job? = null

    init {
        viewModelScope.launch {
            session.authState.collect { authState ->
                val user = (authState as? AuthState.Authenticated)?.user
                val uid = user?.id
                if (uid != currentUid || authState !is AuthState.Authenticated) {
                    currentUid = uid
                    inFlightJob?.cancel()
                    _state.value = VerifyEmailUiState(email = user?.email)
                }
            }
        }
    }

    fun send() {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, message = null) }
        inFlightJob?.cancel()
        inFlightJob = viewModelScope.launch {
            val result = verifier.sendVerificationEmail()
            _state.update {
                when (result) {
                    is AuthResult.Success -> it.copy(busy = false, sent = true, message = null)
                    is AuthResult.Error -> it.copy(busy = false, message = result.message)
                }
            }
        }
    }

    fun check() {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, message = null) }
        inFlightJob?.cancel()
        inFlightJob = viewModelScope.launch {
            val result = verifier.refreshEmailVerification()
            _state.update {
                result.fold(
                    onSuccess = { verified ->
                        if (verified) {
                            it.copy(busy = false, message = null)
                        } else {
                            it.copy(
                                busy = false,
                                message = "Not verified yet. Open the link in your email, then try again."
                            )
                        }
                    },
                    onFailure = { e ->
                        it.copy(busy = false, message = e.message ?: "Failed to verify email")
                    }
                )
            }
        }
    }
}
