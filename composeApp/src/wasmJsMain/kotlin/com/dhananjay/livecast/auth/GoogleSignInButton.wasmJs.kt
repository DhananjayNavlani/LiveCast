package com.dhananjay.livecast.auth

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dhananjay.livecast.ui.theme.LiveCastTheme

/**
 * WasmJS (Web) implementation of Google Sign-In button.
 * Requires Google Identity Services JS SDK integration.
 */
@Composable
actual fun GoogleSignInButton(
    onSignInResult: (idToken: String?) -> Unit,
    enabled: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        backgroundColor = LiveCastTheme.colors.surface,
        elevation = 4.dp
    ) {
        OutlinedButton(
            onClick = {
                // TODO: Integrate Google Identity Services JS SDK
                onSignInResult(null)
            },
            modifier = Modifier.fillMaxWidth().padding(4.dp),
            enabled = false, // Disabled until JS SDK is integrated
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                "🔵 Sign in with Google (Coming Soon)",
                style = LiveCastTheme.typography.body1,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
    }
}

