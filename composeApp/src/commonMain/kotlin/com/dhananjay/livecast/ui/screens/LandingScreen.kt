
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
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
 * Landing screen for the LiveCast web application.
 * This is the first screen users see when they visit the web app.
 * It explains what LiveCast is and provides navigation to features and login.
 */
@Composable
fun LandingScreen(
    onGetStarted: () -> Unit,
    onViewFeatures: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .background(LiveCastTheme.colors.background)
    ) {
        // Hero Section
        HeroSection(
            onGetStarted = onGetStarted,
            onViewFeatures = onViewFeatures
        )
        
        // Quick Features Preview
        QuickFeaturesSection()
        
        // How It Works Section
        HowItWorksSection()
        
        // Call to Action Section
        CallToActionSection(onGetStarted = onGetStarted)
        
        // Footer
        FooterSection()
    }
}

@Composable
private fun HeroSection(
    onGetStarted: () -> Unit,
    onViewFeatures: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        LiveCastTheme.colors.primary.copy(alpha = 0.05f),
                        LiveCastTheme.colors.background
                    )
                )
            )
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // App Icon/Logo placeholder using emoji
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(LiveCastTheme.colors.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "▶",
                    style = LiveCastTheme.typography.h1,
                    color = LiveCastTheme.colors.onPrimary
                )
            }
            
            VerticalSpacer(24)
            
            // Main Headline
            Text(
                text = "LiveCast",
                style = LiveCastTheme.typography.h1,
                color = LiveCastTheme.colors.text,
                textAlign = TextAlign.Center
            )
            
            VerticalSpacer(8)
            
            // Tagline
            Text(
                text = "Real-Time Screen Sharing & Remote Control",
                style = LiveCastTheme.typography.h3,
                color = LiveCastTheme.colors.textSecondary,
                textAlign = TextAlign.Center
            )
            
            VerticalSpacer(16)
            
            // Description
            Text(
                text = "Stream your Android device screen to any browser with WebRTC. " +
                        "Control remotely with touch gestures, unlock devices, and navigate " +
                        "seamlessly - all in real-time with ultra-low latency.",
                style = LiveCastTheme.typography.body1,
                color = LiveCastTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            
            VerticalSpacer(32)
            
            // CTA Buttons
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LiveCastButton(
                    text = "Get Started",
                    onClick = onGetStarted,
                    variant = LiveCastButtonVariant.Primary
                )
                
                HorizontalSpacer(16)
                
                LiveCastButton(
                    text = "View Features",
                    onClick = onViewFeatures,
                    variant = LiveCastButtonVariant.Outlined
                )
            }
        }
    }
}

@Composable
private fun QuickFeaturesSection() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Why Choose LiveCast?",
            style = LiveCastTheme.typography.h2,
            color = LiveCastTheme.colors.text,
            textAlign = TextAlign.Center
        )
        
        VerticalSpacer(24)
        
        // Feature Cards Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            FeatureCard(
                emoji = "⚡",
                title = "Ultra-Low Latency",
                description = "Real-time streaming powered by WebRTC technology",
                modifier = Modifier.weight(1f).padding(8.dp)
            )
            
            FeatureCard(
                emoji = "👁️",
                title = "Watch Anywhere",
                description = "View broadcasts from any modern web browser",
                modifier = Modifier.weight(1f).padding(8.dp)
            )
            
            FeatureCard(
                emoji = "📱",
                title = "Remote Control",
                description = "Control Android devices with touch gestures",
                modifier = Modifier.weight(1f).padding(8.dp)
            )
        }
    }
}

@Composable
private fun FeatureCard(
    emoji: String,
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        backgroundColor = LiveCastTheme.colors.surface,
        elevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(LiveCastTheme.colors.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = emoji,
                    style = LiveCastTheme.typography.h2
                )
            }
            
            VerticalSpacer(16)
            
            Text(
                text = title,
                style = LiveCastTheme.typography.h4,
                color = LiveCastTheme.colors.text,
                textAlign = TextAlign.Center
            )
            
            VerticalSpacer(8)
            
            Text(
                text = description,
                style = LiveCastTheme.typography.body2,
                color = LiveCastTheme.colors.textSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun HowItWorksSection() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(LiveCastTheme.colors.surface)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "How It Works",
            style = LiveCastTheme.typography.h2,
            color = LiveCastTheme.colors.text,
            textAlign = TextAlign.Center
        )
        
        VerticalSpacer(32)
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StepItem(
                number = "1",
                title = "Install App",
                description = "Download LiveCast on your Android device",
                color = Blue900,
                modifier = Modifier.weight(1f).padding(8.dp)
            )
            
            StepItem(
                number = "2",
                title = "Start Broadcasting",
                description = "Sign in as a broadcaster and share your screen",
                color = Green600,
                modifier = Modifier.weight(1f).padding(8.dp)
            )
            
            StepItem(
                number = "3",
                title = "Watch & Control",
                description = "Connect from any browser and take control",
                color = Red600,
                modifier = Modifier.weight(1f).padding(8.dp)
            )
        }
    }
}

@Composable
private fun StepItem(
    number: String,
    title: String,
    description: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(color),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                style = LiveCastTheme.typography.h3,
                color = LiveCastTheme.colors.white
            )
        }
        
        VerticalSpacer(16)
        
        Text(
            text = title,
            style = LiveCastTheme.typography.h4,
            color = LiveCastTheme.colors.text,
            textAlign = TextAlign.Center
        )
        
        VerticalSpacer(8)
        
        Text(
            text = description,
            style = LiveCastTheme.typography.body2,
            color = LiveCastTheme.colors.textSecondary,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun CallToActionSection(onGetStarted: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Ready to Start Streaming?",
                style = LiveCastTheme.typography.h2,
                color = LiveCastTheme.colors.text,
                textAlign = TextAlign.Center
            )
            
            VerticalSpacer(16)
            
            Text(
                text = "Join LiveCast today and experience seamless screen sharing",
                style = LiveCastTheme.typography.body1,
                color = LiveCastTheme.colors.textSecondary,
                textAlign = TextAlign.Center
            )
            
            VerticalSpacer(24)
            
            LiveCastButton(
                text = "Sign In to Watch",
                onClick = onGetStarted,
                variant = LiveCastButtonVariant.Primary
            )
        }
    }
}

@Composable
private fun FooterSection() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(LiveCastTheme.colors.surface)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "🔒 Secure WebRTC Connection",
                    style = LiveCastTheme.typography.body3,
                    color = LiveCastTheme.colors.textSecondary
                )
                
                HorizontalSpacer(24)
                
                Text(
                    text = "📡 Real-Time Streaming",
                    style = LiveCastTheme.typography.body3,
                    color = LiveCastTheme.colors.textSecondary
                )
            }
            
            VerticalSpacer(16)
            
            Text(
                text = "© 2024 LiveCast. Built with Kotlin Multiplatform & Compose.",
                style = LiveCastTheme.typography.body3,
                color = LiveCastTheme.colors.textDisabled,
                textAlign = TextAlign.Center
            )
        }
    }
}
