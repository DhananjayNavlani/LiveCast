
package com.dhananjay.livecast

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dhananjay.livecast.auth.AuthLoginScreen
import com.dhananjay.livecast.auth.LoginViewModel
import com.dhananjay.livecast.ui.components.LiveCastButton
import com.dhananjay.livecast.ui.components.LiveCastButtonVariant
import com.dhananjay.livecast.ui.components.VerticalSpacer
import com.dhananjay.livecast.ui.navigation.Routes
import com.dhananjay.livecast.ui.screens.FeaturesScreen
import com.dhananjay.livecast.ui.screens.LandingScreen
import com.dhananjay.livecast.ui.screens.UserRole
import com.dhananjay.livecast.ui.theme.LiveCastTheme
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel

/**
 * Main App composable for the LiveCast application.
 * This is the entry point for the Compose UI on all platforms.
 * 
 * On non-Android platforms (iOS, Desktop, Web), this provides:
 * - A landing page explaining the app
 * - A features page with detailed information
 * - A login flow that only allows subscribing to broadcasts
 */
@Composable
@Preview
fun App() {
    LiveCastTheme {
        var currentScreen by remember { mutableStateOf<Routes>(Routes.LandingScreen) }
        var selectedRole by remember { mutableStateOf<UserRole?>(null) }
        
        when (currentScreen) {
            is Routes.LandingScreen -> {
                LandingScreen(
                    onGetStarted = {
                        currentScreen = Routes.LoginScreen
                    },
                    onViewFeatures = {
                        currentScreen = Routes.FeaturesScreen
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            is Routes.FeaturesScreen -> {
                FeaturesScreen(
                    onBackClick = {
                        currentScreen = Routes.LandingScreen
                    },
                    onGetStarted = {
                        currentScreen = Routes.LoginScreen
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            is Routes.LoginScreen -> {
                val loginViewModel: LoginViewModel = koinViewModel()
                AuthLoginScreen(
                    viewModel = loginViewModel,
                    onLoginSuccess = { role ->
                        selectedRole = role
                        currentScreen = Routes.StageScreen
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            is Routes.StageScreen -> {
                // Placeholder for stage screen - shows that login was successful
                // This will be implemented with actual WebRTC viewer functionality
                val loginViewModel: LoginViewModel = koinViewModel()
                StageScreenPlaceholder(
                    viewModel = loginViewModel,
                    role = selectedRole ?: UserRole.Subscriber,
                    onLogout = {
                        currentScreen = Routes.LandingScreen
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

/**
 * Placeholder screen shown after successful login.
 * Shows the logged-in user info and provides logout functionality.
 */
@Composable
fun StageScreenPlaceholder(
    viewModel: LoginViewModel,
    role: UserRole,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "🎉 Login Successful!",
            style = LiveCastTheme.typography.h1,
            color = LiveCastTheme.colors.primary,
            textAlign = TextAlign.Center
        )

        VerticalSpacer(24)

        Text(
            text = "Welcome, ${uiState.user?.displayName ?: uiState.user?.email ?: "User"}!",
            style = LiveCastTheme.typography.h2,
            color = LiveCastTheme.colors.text,
            textAlign = TextAlign.Center
        )

        VerticalSpacer(16)

        Text(
            text = when (role) {
                UserRole.Subscriber -> "You're logged in as a Subscriber"
                UserRole.Broadcaster -> "You're logged in as a Broadcaster"
            },
            style = LiveCastTheme.typography.body1,
            color = LiveCastTheme.colors.textSecondary,
            textAlign = TextAlign.Center
        )

        uiState.user?.email?.let { email ->
            VerticalSpacer(8)
            Text(
                text = email,
                style = LiveCastTheme.typography.body2,
                color = LiveCastTheme.colors.textSecondary,
                textAlign = TextAlign.Center
            )
        }

        if (uiState.user?.isAnonymous == true) {
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
            text = "Stage screen coming soon...\nThis is where you'll view broadcasts!",
            style = LiveCastTheme.typography.body1,
            color = LiveCastTheme.colors.textSecondary,
            textAlign = TextAlign.Center
        )

        VerticalSpacer(32)

        LiveCastButton(
            text = "Sign Out",
            onClick = {
                viewModel.signOut()
                onLogout()
            },
            variant = LiveCastButtonVariant.Secondary
        )
    }
}

