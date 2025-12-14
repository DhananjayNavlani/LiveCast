
package com.dhananjay.livecast.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dhananjay.livecast.ui.components.HorizontalSpacer
import com.dhananjay.livecast.ui.components.LiveCastButton
import com.dhananjay.livecast.ui.components.LiveCastButtonVariant
import com.dhananjay.livecast.ui.components.VerticalSpacer
import com.dhananjay.livecast.ui.theme.Blue900
import com.dhananjay.livecast.ui.theme.Green600
import com.dhananjay.livecast.ui.theme.LiveCastTheme
import com.dhananjay.livecast.ui.theme.Red600

/**
 * Detailed features screen for LiveCast web application.
 * Displays all the features and capabilities of the application.
 */
@Composable
fun FeaturesScreen(
    onBackClick: () -> Unit,
    onGetStarted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(LiveCastTheme.colors.background)
    ) {
        // Header with back button
        FeaturesHeader(onBackClick = onBackClick)
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            // Hero Section
            FeaturesHeroSection()
            
            // Core Features
            CoreFeaturesSection()
            
            // Technical Features
            TechnicalFeaturesSection()
            
            // Platform Support
            PlatformSupportSection()
            
            // Security Features
            SecurityFeaturesSection()
            
            // CTA Section
            FeaturesCTASection(onGetStarted = onGetStarted)
        }
    }
}

@Composable
private fun FeaturesHeader(onBackClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(LiveCastTheme.colors.surface)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LiveCastButton(
            text = "← Back",
            onClick = onBackClick,
            variant = LiveCastButtonVariant.Text
        )
        
        Text(
            text = "Features",
            style = LiveCastTheme.typography.h3,
            color = LiveCastTheme.colors.text,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center
        )
        
        // Spacer for balance
        Box(modifier = Modifier.size(72.dp))
    }
}

@Composable
private fun FeaturesHeroSection() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Powerful Features",
            style = LiveCastTheme.typography.h1,
            color = LiveCastTheme.colors.text,
            textAlign = TextAlign.Center
        )
        
        VerticalSpacer(16)
        
        Text(
            text = "Everything you need for seamless screen sharing and remote device control",
            style = LiveCastTheme.typography.body1,
            color = LiveCastTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Composable
private fun CoreFeaturesSection() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(LiveCastTheme.colors.surface)
            .padding(24.dp)
    ) {
        SectionTitle("Core Features")
        
        VerticalSpacer(24)
        
        FeatureDetailCard(
            emoji = "📺",
            title = "Real-Time Screen Streaming",
            description = "Share your Android device screen in real-time with minimal latency. " +
                    "Powered by WebRTC technology for peer-to-peer streaming with automatic " +
                    "quality adjustment based on network conditions.",
            highlights = listOf(
                "1080p video quality support",
                "30 FPS smooth streaming",
                "Automatic bitrate adjustment",
                "Works over WiFi and mobile data"
            ),
            accentColor = Blue900
        )
        
        VerticalSpacer(16)
        
        FeatureDetailCard(
            emoji = "👆",
            title = "Touch Gesture Control",
            description = "Control the broadcasting device remotely using intuitive touch gestures. " +
                    "All gestures are captured and transmitted in real-time to provide a " +
                    "seamless remote control experience.",
            highlights = listOf(
                "Single tap, double tap, long press",
                "Swipe gestures (up, down, left, right)",
                "Pinch to zoom support",
                "Smooth drag and scroll"
            ),
            accentColor = Green600
        )
        
        VerticalSpacer(16)
        
        FeatureDetailCard(
            emoji = "🔓",
            title = "Device Navigation Controls",
            description = "Navigate the remote device as if you were holding it in your hands. " +
                    "Access system-level controls for complete device management.",
            highlights = listOf(
                "Home button functionality",
                "Back navigation",
                "Recent apps access",
                "Device unlock capability"
            ),
            accentColor = Red600
        )
    }
}

@Composable
private fun TechnicalFeaturesSection() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        SectionTitle("Technical Features")
        
        VerticalSpacer(24)
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TechFeatureCard(
                emoji = "🌐",
                title = "WebRTC",
                description = "Industry-standard real-time communication protocol",
                modifier = Modifier.weight(1f)
            )
            
            TechFeatureCard(
                emoji = "📡",
                title = "Firebase Signaling",
                description = "Reliable connection establishment via Firestore",
                modifier = Modifier.weight(1f)
            )
            
            TechFeatureCard(
                emoji = "📊",
                title = "DataChannel",
                description = "Low-latency bidirectional data transmission",
                modifier = Modifier.weight(1f)
            )
        }
        
        VerticalSpacer(16)
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TechFeatureCard(
                emoji = "♿",
                title = "Accessibility Service",
                description = "System-level access for remote control on Android",
                modifier = Modifier.weight(1f)
            )
            
            TechFeatureCard(
                emoji = "🖥️",
                title = "Media Projection",
                description = "Native Android screen capture API",
                modifier = Modifier.weight(1f)
            )
            
            TechFeatureCard(
                emoji = "🔄",
                title = "Auto Reconnect",
                description = "Automatic connection recovery on network changes",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun PlatformSupportSection() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(LiveCastTheme.colors.surface)
            .padding(24.dp)
    ) {
        SectionTitle("Platform Support")
        
        VerticalSpacer(24)
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            PlatformCard(
                emoji = "🤖",
                platform = "Android",
                role = "Broadcaster & Viewer",
                description = "Full broadcasting and viewing capabilities",
                isFullSupport = true
            )
            
            PlatformCard(
                emoji = "🌐",
                platform = "Web (WASM)",
                role = "Viewer Only",
                description = "Watch and control broadcasts from any browser",
                isFullSupport = false
            )
            
            PlatformCard(
                emoji = "🖥️",
                platform = "Desktop",
                role = "Viewer Only",
                description = "Native desktop app for viewing broadcasts",
                isFullSupport = false
            )
            
            PlatformCard(
                emoji = "🍎",
                platform = "iOS",
                role = "Viewer Only",
                description = "Watch broadcasts on iPhone and iPad",
                isFullSupport = false
            )
        }
    }
}

@Composable
private fun SecurityFeaturesSection() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        SectionTitle("Security & Privacy")
        
        VerticalSpacer(24)
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SecurityCard(
                emoji = "🔐",
                title = "End-to-End Encryption",
                description = "All video streams are encrypted using DTLS-SRTP",
                modifier = Modifier.weight(1f)
            )
            
            SecurityCard(
                emoji = "🔒",
                title = "Peer-to-Peer Connection",
                description = "Direct connection between devices, no video data on servers",
                modifier = Modifier.weight(1f)
            )
            
            SecurityCard(
                emoji = "👤",
                title = "Firebase Authentication",
                description = "Secure user authentication with multiple providers",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun FeaturesCTASection(onGetStarted: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(LiveCastTheme.colors.surface)
            .padding(48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Ready to Experience LiveCast?",
                style = LiveCastTheme.typography.h2,
                color = LiveCastTheme.colors.text,
                textAlign = TextAlign.Center
            )
            
            VerticalSpacer(16)
            
            Text(
                text = "Sign in now and start watching broadcasts",
                style = LiveCastTheme.typography.body1,
                color = LiveCastTheme.colors.textSecondary,
                textAlign = TextAlign.Center
            )
            
            VerticalSpacer(24)
            
            LiveCastButton(
                text = "Get Started",
                onClick = onGetStarted,
                variant = LiveCastButtonVariant.Primary
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = LiveCastTheme.typography.h2,
        color = LiveCastTheme.colors.text
    )
}

@Composable
private fun FeatureDetailCard(
    emoji: String,
    title: String,
    description: String,
    highlights: List<String>,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        backgroundColor = LiveCastTheme.colors.background,
        elevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(24.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Emoji icon
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = emoji,
                    style = LiveCastTheme.typography.h2
                )
            }
            
            HorizontalSpacer(20)
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = LiveCastTheme.typography.h3,
                    color = LiveCastTheme.colors.text
                )
                
                VerticalSpacer(8)
                
                Text(
                    text = description,
                    style = LiveCastTheme.typography.body1,
                    color = LiveCastTheme.colors.textSecondary
                )
                
                VerticalSpacer(16)
                
                Divider(color = LiveCastTheme.colors.outline)
                
                VerticalSpacer(16)
                
                highlights.forEach { highlight ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(accentColor)
                        )
                        HorizontalSpacer(12)
                        Text(
                            text = highlight,
                            style = LiveCastTheme.typography.body2,
                            color = LiveCastTheme.colors.text
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TechFeatureCard(
    emoji: String,
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        backgroundColor = LiveCastTheme.colors.surface,
        elevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = emoji,
                style = LiveCastTheme.typography.h2
            )
            
            VerticalSpacer(12)
            
            Text(
                text = title,
                style = LiveCastTheme.typography.h4,
                color = LiveCastTheme.colors.text,
                textAlign = TextAlign.Center
            )
            
            VerticalSpacer(8)
            
            Text(
                text = description,
                style = LiveCastTheme.typography.body3,
                color = LiveCastTheme.colors.textSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun PlatformCard(
    emoji: String,
    platform: String,
    role: String,
    description: String,
    isFullSupport: Boolean
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        backgroundColor = if (isFullSupport) 
            LiveCastTheme.colors.primary.copy(alpha = 0.05f) 
        else 
            LiveCastTheme.colors.background,
        elevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = emoji,
                style = LiveCastTheme.typography.h1
            )
            
            VerticalSpacer(12)
            
            Text(
                text = platform,
                style = LiveCastTheme.typography.h4,
                color = LiveCastTheme.colors.text,
                textAlign = TextAlign.Center
            )
            
            VerticalSpacer(4)
            
            Text(
                text = role,
                style = LiveCastTheme.typography.body3,
                color = if (isFullSupport) Green600 else LiveCastTheme.colors.textSecondary,
                textAlign = TextAlign.Center
            )
            
            VerticalSpacer(8)
            
            Text(
                text = description,
                style = LiveCastTheme.typography.body3,
                color = LiveCastTheme.colors.textSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SecurityCard(
    emoji: String,
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        backgroundColor = LiveCastTheme.colors.surface,
        elevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = emoji,
                style = LiveCastTheme.typography.h2
            )
            
            VerticalSpacer(12)
            
            Text(
                text = title,
                style = LiveCastTheme.typography.h4,
                color = LiveCastTheme.colors.text,
                textAlign = TextAlign.Center
            )
            
            VerticalSpacer(8)
            
            Text(
                text = description,
                style = LiveCastTheme.typography.body3,
                color = LiveCastTheme.colors.textSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}
