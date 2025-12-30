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
 * iOS implementation of Google Sign-In button.
 * Note: Full Google Sign-In requires GoogleSignIn-iOS pod integration.
 * This is a placeholder that informs users the feature requires additional setup.
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
                // TODO: Integrate GoogleSignIn-iOS pod for native Google Sign-In
                // For now, this is a placeholder
                onSignInResult(null)
            },
            modifier = Modifier.fillMaxWidth().padding(4.dp),
            enabled = false, // Disabled until GoogleSignIn-iOS is integrated
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

