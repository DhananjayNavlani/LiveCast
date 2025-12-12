
package com.dhananjay.livecast

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.dhananjay.livecast.ui.navigation.Routes
import com.dhananjay.livecast.ui.screens.FeaturesScreen
import com.dhananjay.livecast.ui.screens.LandingScreen
import com.dhananjay.livecast.ui.screens.LoginScreen
import com.dhananjay.livecast.ui.screens.UserRole
import com.dhananjay.livecast.ui.theme.LiveCastTheme
import org.jetbrains.compose.ui.tooling.preview.Preview

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
                LoginScreen(
                    onRoleSelected = { role ->
                        selectedRole = role
                        // For now, stay on login screen after role selection
                        // In a full implementation, this would navigate to the stage screen
                        // after authentication
                    },
                    onBackToLanding = {
                        currentScreen = Routes.LandingScreen
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            is Routes.StageScreen -> {
                // Placeholder for stage screen
                // This will be implemented with actual WebRTC viewer functionality
                LoginScreen(
                    onRoleSelected = { },
                    onBackToLanding = {
                        currentScreen = Routes.LandingScreen
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
