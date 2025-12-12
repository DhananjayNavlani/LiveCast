package com.dhananjay.livecast.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dhananjay.livecast.platform.getPlatformCapabilities
import com.dhananjay.livecast.ui.components.LiveCastButton
import com.dhananjay.livecast.ui.components.LiveCastButtonVariant
import com.dhananjay.livecast.ui.components.VerticalSpacer
import com.dhananjay.livecast.ui.screens.UserRole
import com.dhananjay.livecast.ui.theme.LiveCastTheme

@Composable
fun AuthLoginScreen(
    viewModel: LoginViewModel,
    onLoginSuccess: (UserRole) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val platformCapabilities = getPlatformCapabilities()
    val focusManager = LocalFocusManager.current

    var passwordVisible by remember { mutableStateOf(false) }
    var selectedRole by remember { mutableStateOf<UserRole?>(null) }

    LaunchedEffect(uiState.isLoggedIn, selectedRole) {
        if (uiState.isLoggedIn && selectedRole != null) {
            onLoginSuccess(selectedRole!!)
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "LiveCast",
            style = LiveCastTheme.typography.h1,
            color = LiveCastTheme.colors.primary,
            textAlign = TextAlign.Center
        )

        VerticalSpacer(8)

        Text(
            text = if (uiState.isSignUpMode) "Create Account" else "Sign In",
            style = LiveCastTheme.typography.h2,
            color = LiveCastTheme.colors.text,
            textAlign = TextAlign.Center
        )

        VerticalSpacer(32)

        OutlinedTextField(
            value = uiState.email,
            onValueChange = viewModel::onEmailChange,
            label = { Text("Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Down) }
            ),
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.isLoading,
            colors = TextFieldDefaults.outlinedTextFieldColors(
                focusedBorderColor = LiveCastTheme.colors.primary,
                cursorColor = LiveCastTheme.colors.primary
            )
        )

        VerticalSpacer(16)

        OutlinedTextField(
            value = uiState.password,
            onValueChange = viewModel::onPasswordChange,
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                    if (selectedRole != null) viewModel.signIn()
                }
            ),
            trailingIcon = {
                TextButton(onClick = { passwordVisible = !passwordVisible }) {
                    Text(if (passwordVisible) "Hide" else "Show", color = LiveCastTheme.colors.textSecondary)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.isLoading,
            colors = TextFieldDefaults.outlinedTextFieldColors(
                focusedBorderColor = LiveCastTheme.colors.primary,
                cursorColor = LiveCastTheme.colors.primary
            )
        )

        VerticalSpacer(8)

        if (!uiState.isSignUpMode) {
            TextButton(onClick = viewModel::forgotPassword, enabled = !uiState.isLoading) {
                Text("Forgot Password?", color = LiveCastTheme.colors.primary)
            }
        }

        VerticalSpacer(16)

        uiState.errorMessage?.let { error ->
            Text(
                text = error,
                color = if (error.contains("sent", ignoreCase = true)) Color(0xFF4CAF50) else LiveCastTheme.colors.error,
                style = LiveCastTheme.typography.body2,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            VerticalSpacer(16)
        }

        Text(
            text = "Select your role:",
            style = LiveCastTheme.typography.body2,
            color = LiveCastTheme.colors.textSecondary
        )

        VerticalSpacer(12)

        LiveCastButton(
            text = if (uiState.isSignUpMode) "Sign Up as Subscriber" else "Sign In as Subscriber",
            onClick = {
                selectedRole = UserRole.Subscriber
                viewModel.signIn()
            },
            variant = LiveCastButtonVariant.Primary,
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.isLoading
        )

        if (platformCapabilities.canBroadcast) {
            VerticalSpacer(12)
            LiveCastButton(
                text = if (uiState.isSignUpMode) "Sign Up as Broadcaster" else "Sign In as Broadcaster",
                onClick = {
                    selectedRole = UserRole.Broadcaster
                    viewModel.signIn()
                },
                variant = LiveCastButtonVariant.Secondary,
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isLoading
            )
        }

        VerticalSpacer(24)

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Divider(modifier = Modifier.weight(1f))
            Text("  OR  ", color = LiveCastTheme.colors.textSecondary, style = LiveCastTheme.typography.body3)
            Divider(modifier = Modifier.weight(1f))
        }

        VerticalSpacer(24)

        OutlinedButton(
            onClick = {
                selectedRole = UserRole.Subscriber
                viewModel.signInAsGuest()
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.isLoading
        ) {
            Text("Continue as Guest")
        }

        VerticalSpacer(16)

        TextButton(onClick = viewModel::toggleSignUpMode, enabled = !uiState.isLoading) {
            Text(
                text = if (uiState.isSignUpMode) "Already have an account? Sign In" else "Don't have an account? Sign Up",
                color = LiveCastTheme.colors.primary
            )
        }

        if (uiState.isLoading) {
            VerticalSpacer(16)
            CircularProgressIndicator(color = LiveCastTheme.colors.primary, modifier = Modifier.size(32.dp))
        }

        VerticalSpacer(24)

        Text(
            text = "Running on ${platformCapabilities.platformName}",
            style = LiveCastTheme.typography.body3,
            color = LiveCastTheme.colors.textDisabled,
            textAlign = TextAlign.Center
        )
    }
}
