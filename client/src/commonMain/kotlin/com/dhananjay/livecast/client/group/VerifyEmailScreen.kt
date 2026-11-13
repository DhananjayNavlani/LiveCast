package com.dhananjay.livecast.client.group

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dhananjay.livecast.ui.components.LiveCastButton
import com.dhananjay.livecast.ui.components.LiveCastButtonVariant
import com.dhananjay.livecast.ui.components.VerticalSpacer
import com.dhananjay.livecast.ui.theme.LiveCastTheme
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun VerifyEmailScreen(
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VerifyEmailViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Verify Email",
                        style = LiveCastTheme.typography.h2,
                        color = LiveCastTheme.colors.onPrimary
                    )
                },
                backgroundColor = LiveCastTheme.colors.primary,
                actions = {
                    TextButton(onClick = onSignOut) {
                        Text(
                            text = "Sign out",
                            color = LiveCastTheme.colors.onPrimary,
                            style = LiveCastTheme.typography.body2
                        )
                    }
                }
            )
        },
        backgroundColor = LiveCastTheme.colors.background,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Verify Your Email",
                style = LiveCastTheme.typography.h2,
                color = LiveCastTheme.colors.text,
                textAlign = TextAlign.Center
            )

            VerticalSpacer(12)

            val emailText = state.email?.let { "We sent or will send a verification link to $it." }
                ?: "Please verify your email address to continue."
            Text(
                text = emailText,
                style = LiveCastTheme.typography.body1,
                color = LiveCastTheme.colors.textSecondary,
                textAlign = TextAlign.Center
            )

            VerticalSpacer(24)

            if (state.sent) {
                Text(
                    text = "Verification email sent. Check your inbox and click the link.",
                    style = LiveCastTheme.typography.body2,
                    color = LiveCastTheme.colors.success,
                    textAlign = TextAlign.Center
                )
                VerticalSpacer(16)
            }

            state.message?.let { msg ->
                Text(
                    text = msg,
                    style = LiveCastTheme.typography.body2,
                    color = LiveCastTheme.colors.error,
                    textAlign = TextAlign.Center
                )
                VerticalSpacer(16)
            }

            LiveCastButton(
                text = if (state.sent) "Resend verification email" else "Send verification email",
                onClick = { viewModel.send() },
                enabled = !state.busy,
                variant = LiveCastButtonVariant.Outlined,
                modifier = Modifier.fillMaxWidth()
            )

            VerticalSpacer(12)

            LiveCastButton(
                text = "I've verified my email",
                onClick = { viewModel.check() },
                enabled = !state.busy,
                variant = LiveCastButtonVariant.Primary,
                modifier = Modifier.fillMaxWidth()
            )

            VerticalSpacer(24)

            TextButton(onClick = onSignOut) {
                Text(
                    text = "Sign out",
                    style = LiveCastTheme.typography.button,
                    color = LiveCastTheme.colors.primary
                )
            }
        }
    }
}
