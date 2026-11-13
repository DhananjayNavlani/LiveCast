package com.dhananjay.livecast.cast.ui.stage

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.SignalCellular4Bar
import androidx.compose.material.icons.filled.SignalWifiOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.dhananjay.livecast.cast.data.PermissionManager
import com.dhananjay.livecast.cast.data.model.LiveCastUser
import com.dhananjay.livecast.cast.data.repositories.PreferencesRepository
import org.koin.compose.koinInject


@Composable
fun StageScreen(
    state: List<LiveCastUser>,
    onStart: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    preferencesRepository: PreferencesRepository = koinInject(),
    permissionManager: PermissionManager = koinInject(),
) {

    val user by preferencesRepository.getUser().collectAsStateWithLifecycle(null)
    Box(modifier = modifier.fillMaxSize()) {

/*        val text = when (state) {
            WebRTCSessionState.Offline -> {
                enabledCall = false
                stringResource(id = R.string.button_start_session)
            }
            WebRTCSessionState.Impossible -> {
                enabledCall = false
                stringResource(id = R.string.session_impossible)
            }
            WebRTCSessionState.Ready -> {
                enabledCall = true
                stringResource(id = R.string.session_ready)
            }
            WebRTCSessionState.Creating -> {
                enabledCall = true
                stringResource(id = R.string.session_creating)
            }
            WebRTCSessionState.Active -> {
                enabledCall = false
                stringResource(id = R.string.session_active)
            }
        }

        Button(
            modifier = Modifier.align(Alignment.Center),
            enabled = enabledCall,
            onClick = { onJoinCall.invoke() }
        ) {
            Text(
                text = text,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )
        }*/
        IconButton(
            onClick = onLogout,
            modifier = modifier.align(Alignment.TopEnd)
        ) {
            Icon(Icons.AutoMirrored.Filled.ExitToApp, null)
        }

        Column(
            modifier = modifier.align(Alignment.TopCenter),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AsyncImage(
                model = user?.photoUrl,
                contentDescription = null,
                modifier = modifier
                    .align(Alignment.CenterHorizontally)
                    .fillMaxSize(0.2f)
            )
            Text("Welcome ${user?.name}")

        }

        if (user?.isViewer == true) {
            Column(
                modifier = modifier
                    .align(Alignment.Center)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Online Broadcasters",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${state.size} device(s) available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state) { onlineUser ->
                        OnlineUserCard(
                            user = onlineUser,
                            onClick = { onStart() }
                        )
                    }
                }

                if (state.isEmpty()) {
                    Text(
                        text = "No broadcasters online",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            val context = LocalContext.current
            Column(
                modifier = modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val isEnabled = permissionManager.isAccessibilityEnabled()

                Text("Accessibility Service is ${if (isEnabled) "enabled" else "disabled"}")
                if (!isEnabled) {
                    Button(
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        },
                    ) {
                        Text(text = "Enable Accessibility Service")
                    }
                }
            }
        }

    }

}

/**
 * Card component displaying online user with device information
 */
@Composable
fun OnlineUserCard(
    user: LiveCastUser,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // User avatar
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                if (user.photoUrl != null) {
                    AsyncImage(
                        model = user.photoUrl,
                        contentDescription = "Profile",
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PhoneAndroid,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // User and device info
            Column(modifier = Modifier.weight(1f)) {
                // User name
                Text(
                    text = user.name.ifEmpty { "Unknown Device" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Device name
                user.device?.let { device ->
                    Text(
                        text = device.deviceName.ifEmpty { "${device.manufacturer} ${device.model}" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Device stats row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Screen resolution
                        DeviceInfoChip(
                            text = "${device.screenWidth}x${device.screenHeight}",
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.PhoneAndroid,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        )

                        // Battery
                        DeviceInfoChip(
                            text = "${device.batteryLevel}%",
                            icon = {
                                Icon(
                                    imageVector = if (device.isCharging)
                                        Icons.Default.BatteryChargingFull
                                    else
                                        Icons.Default.BatteryFull,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = getBatteryColor(device.batteryLevel, device.isCharging)
                                )
                            }
                        )

                        // Network
                        DeviceInfoChip(
                            text = device.networkType.replaceFirstChar { it.uppercase() },
                            icon = {
                                Icon(
                                    imageVector = getNetworkIcon(device.networkType),
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        )
                    }
                } ?: run {
                    // Fallback for users without device info
                    Text(
                        text = "Android ${user.widthPixels}x${user.heightPixels}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Online indicator
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF4CAF50)) // Green
            )
        }
    }
}

/**
 * Small chip for displaying device info
 */
@Composable
private fun DeviceInfoChip(
    text: String,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        icon()
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 11.sp
        )
    }
}

/**
 * Get battery color based on level and charging status
 */
@Composable
private fun getBatteryColor(level: Int, isCharging: Boolean): Color {
    return when {
        isCharging -> Color(0xFF4CAF50) // Green
        level <= 15 -> Color(0xFFF44336) // Red
        level <= 30 -> Color(0xFFFF9800) // Orange
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

/**
 * Get network icon based on type
 */
private fun getNetworkIcon(networkType: String) = when (networkType.lowercase()) {
    "wifi" -> Icons.Default.Wifi
    "mobile" -> Icons.Default.SignalCellular4Bar
    else -> Icons.Default.SignalWifiOff
}
