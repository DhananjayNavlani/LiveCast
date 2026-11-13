package com.dhananjay.livecast.client.pairing

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dhananjay.livecast.protocol.HostDevice
import com.dhananjay.livecast.ui.components.HorizontalSpacer
import com.dhananjay.livecast.ui.components.LiveCastButton
import com.dhananjay.livecast.ui.components.LiveCastButtonVariant
import com.dhananjay.livecast.ui.components.VerticalSpacer
import com.dhananjay.livecast.ui.theme.LiveCastTheme
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun MyDevicesScreen(
    onAddDevice: () -> Unit,
    onDeviceClick: (HostDevice) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MyDevicesViewModel = koinViewModel(),
    onSignOut: (() -> Unit)? = null
) {
    val devices by viewModel.devices.collectAsState()
    val devicesError by viewModel.devicesError.collectAsState()
    var deviceToRemove by remember { mutableStateOf<HostDevice?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "My Devices",
                        style = LiveCastTheme.typography.h2,
                        color = LiveCastTheme.colors.onPrimary
                    )
                },
                backgroundColor = LiveCastTheme.colors.primary,
                actions = {
                    if (onSignOut != null) {
                        TextButton(onClick = onSignOut) {
                            Text(
                                text = "Sign out",
                                color = LiveCastTheme.colors.onPrimary,
                                style = LiveCastTheme.typography.body2
                            )
                        }
                    }
                }
            )
        },
        backgroundColor = LiveCastTheme.colors.background,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Supervised Devices",
                    style = LiveCastTheme.typography.h3,
                    color = LiveCastTheme.colors.text
                )

                LiveCastButton(
                    text = "+ Add device",
                    onClick = onAddDevice,
                    variant = LiveCastButtonVariant.Primary
                )
            }

            VerticalSpacer(24)

            devicesError?.let { err ->
                Text(
                    text = err,
                    color = LiveCastTheme.colors.error,
                    style = LiveCastTheme.typography.body2,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                )
            }

            if (devices.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(LiveCastTheme.colors.primary.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "📱",
                                style = LiveCastTheme.typography.h1
                            )
                        }

                        VerticalSpacer(16)

                        Text(
                            text = "No devices yet",
                            style = LiveCastTheme.typography.h3,
                            color = LiveCastTheme.colors.textSecondary,
                            textAlign = TextAlign.Center
                        )

                        VerticalSpacer(8)

                        Text(
                            text = "Tap 'Add device' to connect with a child's device using a 6-digit code.",
                            style = LiveCastTheme.typography.body2,
                            color = LiveCastTheme.colors.textDisabled,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(devices, key = { it.id }) { device ->
                        DeviceCard(
                            device = device,
                            onClick = { onDeviceClick(device) },
                            onRemove = { deviceToRemove = device }
                        )
                    }
                }
            }
        }
    }

    deviceToRemove?.let { device ->
        AlertDialog(
            onDismissRequest = { deviceToRemove = null },
            title = {
                Text(
                    text = "Remove Supervision",
                    style = LiveCastTheme.typography.h3
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to remove supervision of ${device.displayName}? You will need a new code from the device to reconnect.",
                    style = LiveCastTheme.typography.body2
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.remove(device.id)
                        deviceToRemove = null
                    }
                ) {
                    Text(
                        text = "Remove",
                        color = LiveCastTheme.colors.error,
                        style = LiveCastTheme.typography.button
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deviceToRemove = null }) {
                    Text(
                        text = "Cancel",
                        color = LiveCastTheme.colors.textSecondary,
                        style = LiveCastTheme.typography.button
                    )
                }
            }
        )
    }
}

@Composable
private fun DeviceCard(
    device: HostDevice,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        backgroundColor = LiveCastTheme.colors.surface,
        elevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = device.displayName,
                    style = LiveCastTheme.typography.h3,
                    color = LiveCastTheme.colors.text
                )

                VerticalSpacer(4)

                Text(
                    text = device.model,
                    style = LiveCastTheme.typography.body2,
                    color = LiveCastTheme.colors.textSecondary
                )
            }

            HorizontalSpacer(8)

            LiveCastButton(
                text = "Remove",
                onClick = onRemove,
                variant = LiveCastButtonVariant.Outlined
            )
        }
    }
}
