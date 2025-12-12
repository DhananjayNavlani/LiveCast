
package com.dhananjay.livecast

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.dhananjay.livecast.ui.screens.LoginScreen
import com.dhananjay.livecast.ui.screens.UserRole
import com.dhananjay.livecast.ui.theme.LiveCastTheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Main App composable for the LiveCast application.
 * This is the entry point for the Compose UI on all platforms.
 * 
 * On non-Android platforms (iOS, Desktop, Web), this provides a simplified
 * login flow that only allows subscribing to broadcasts.
 */
@Composable
@Preview
fun App() {
    LiveCastTheme {
        var selectedRole by remember { mutableStateOf<UserRole?>(null) }
        
        when (selectedRole) {
            null -> {
                // Show login screen for role selection
                LoginScreen(
                    onRoleSelected = { role ->
                        selectedRole = role
                        // On non-Android platforms, role will always be Subscriber
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            is UserRole.Subscriber -> {
                // TODO: Implement subscriber flow
                // This will be handled by platform-specific implementations
                // For now, show a placeholder or the login screen
                LoginScreen(
                    onRoleSelected = { role ->
                        selectedRole = role
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            is UserRole.Broadcaster -> {
                // This case should only happen on Android
                // Android will use its own MainActivity with full navigation
                LoginScreen(
                    onRoleSelected = { role ->
                        selectedRole = role
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
