package com.dhananjay.livecast.client.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dhananjay.livecast.auth.LoginViewModel
import com.dhananjay.livecast.ui.components.LiveCastButton
import com.dhananjay.livecast.ui.components.LiveCastButtonVariant
import com.dhananjay.livecast.ui.components.VerticalSpacer
import com.dhananjay.livecast.ui.theme.LiveCastTheme

@Composable
fun ViewerScreen(
    viewModel: LoginViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val user = uiState.user
    val displayName = user?.displayName?.takeIf { it.isNotBlank() }
        ?: user?.email?.takeIf { it.isNotBlank() }
        ?: if (user?.isAnonymous == true) "Guest" else "Subscriber"

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(LiveCastTheme.colors.background)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Icon / avatar placeholder
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(LiveCastTheme.colors.primary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "📺",
                style = LiveCastTheme.typography.h1
            )
        }

        VerticalSpacer(24)

        Text(
            text = "Welcome, $displayName!",
            style = LiveCastTheme.typography.h2,
            color = LiveCastTheme.colors.text,
            textAlign = TextAlign.Center
        )

        user?.email?.let { email ->
            if (email.isNotBlank() && email != displayName) {
                VerticalSpacer(8)
                Text(
                    text = email,
                    style = LiveCastTheme.typography.body2,
                    color = LiveCastTheme.colors.textSecondary,
                    textAlign = TextAlign.Center
                )
            }
        }

        if (user?.isAnonymous == true) {
            VerticalSpacer(8)
            Text(
                text = "(Guest Account)",
                style = LiveCastTheme.typography.body2,
                color = LiveCastTheme.colors.textDisabled,
                textAlign = TextAlign.Center
            )
        }

        VerticalSpacer(32)

        Text(
            text = "Waiting for a broadcaster…",
            style = LiveCastTheme.typography.h3,
            color = LiveCastTheme.colors.primary,
            textAlign = TextAlign.Center
        )

        VerticalSpacer(8)

        Text(
            text = "Pairing with a broadcaster will come later.",
            style = LiveCastTheme.typography.body2,
            color = LiveCastTheme.colors.textSecondary,
            textAlign = TextAlign.Center
        )

        VerticalSpacer(32)

        LiveCastButton(
            text = "Logout",
            onClick = { viewModel.signOut() },
            variant = LiveCastButtonVariant.Secondary
        )
    }
}
