
package com.dhananjay.livecast.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dhananjay.livecast.platform.getPlatformCapabilities
import com.dhananjay.livecast.ui.components.LiveCastButton
import com.dhananjay.livecast.ui.components.LiveCastButtonVariant
import com.dhananjay.livecast.ui.components.VerticalSpacer
import com.dhananjay.livecast.ui.theme.LiveCastTheme

/**
 * User role selection for sign-in
 */
sealed class UserRole {
    /**
     * Subscriber/Viewer - can watch broadcasts (available on all platforms)
     */
    data object Subscriber : UserRole()
    
    /**
     * Broadcaster - can share screen (only available on Android)
     */
    data object Broadcaster : UserRole()
}

/**
 * Shared LoginScreen composable that handles role selection.
 * 
 * - On Android: Shows both "Sign In As Subscriber" and "Sign In As Broadcaster" options
 * - On iOS, Desktop, Web: Shows only "Sign In As Subscriber" option
 * 
 * @param onRoleSelected Callback when user selects a role. Will always be Subscriber for non-Android platforms.
 * @param modifier Modifier for the screen
 */
@Composable
fun LoginScreen(
    onRoleSelected: (UserRole) -> Unit,
    modifier: Modifier = Modifier
) {
    val platformCapabilities = getPlatformCapabilities()
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Welcome text
        Text(
            text = "Welcome to LiveCast",
            style = LiveCastTheme.typography.h1,
            color = LiveCastTheme.colors.text,
            textAlign = TextAlign.Center
        )
        
        VerticalSpacer(8)
        
        Text(
            text = "Select how you want to use the app",
            style = LiveCastTheme.typography.body1,
            color = LiveCastTheme.colors.textSecondary,
            textAlign = TextAlign.Center
        )
        
        VerticalSpacer(32)
        
        // Sign In As Subscriber - available on all platforms
        LiveCastButton(
            text = "Sign In As Subscriber",
            onClick = { onRoleSelected(UserRole.Subscriber) },
            variant = LiveCastButtonVariant.Primary,
            modifier = Modifier.fillMaxWidth()
        )
        
        // Sign In As Broadcaster - only available on Android
        if (platformCapabilities.canBroadcast) {
            VerticalSpacer(16)
            
            LiveCastButton(
                text = "Sign In As Broadcaster",
                onClick = { onRoleSelected(UserRole.Broadcaster) },
                variant = LiveCastButtonVariant.Secondary,
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        VerticalSpacer(24)
        
        // Platform indicator (for debugging/info)
        Text(
            text = "Running on ${platformCapabilities.platformName}",
            style = LiveCastTheme.typography.body3,
            color = LiveCastTheme.colors.textDisabled,
            textAlign = TextAlign.Center
        )
    }
}
