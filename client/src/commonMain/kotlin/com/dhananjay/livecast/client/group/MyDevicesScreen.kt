package com.dhananjay.livecast.client.group

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
import androidx.compose.ui.graphics.Color
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
    onManageGroups: () -> Unit,
    onDeviceClick: (HostDevice) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MyDevicesViewModel = koinViewModel(),
    onSignOut: (() -> Unit)? = null,
) {
    val state by viewModel.state.collectAsState()
    var rowToRemove by remember { mutableStateOf<DeviceRow?>(null) }

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
                    if (!state.guest) {
                        TextButton(onClick = onManageGroups) {
                            Text(
                                text = "Groups",
                                color = LiveCastTheme.colors.onPrimary,
                                style = LiveCastTheme.typography.body2
                            )
                        }
                    }
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
            if (state.guest) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Sign in with an account to see devices",
                            style = LiveCastTheme.typography.h3,
                            color = LiveCastTheme.colors.textSecondary,
                            textAlign = TextAlign.Center
                        )
                        if (onSignOut != null) {
                            VerticalSpacer(24)
                            LiveCastButton(
                                text = "Sign out",
                                onClick = onSignOut,
                                variant = LiveCastButtonVariant.Primary
                            )
                        }
                    }
                }
            } else {
                state.error?.let { err ->
                    Text(
                        text = err,
                        color = LiveCastTheme.colors.error,
                        style = LiveCastTheme.typography.body2,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    )
                }

                if (state.rows.isEmpty()) {
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

                            if (!state.hasGroups) {
                                Text(
                                    text = "No groups yet",
                                    style = LiveCastTheme.typography.h3,
                                    color = LiveCastTheme.colors.textSecondary,
                                    textAlign = TextAlign.Center
                                )

                                VerticalSpacer(8)

                                Text(
                                    text = "Tap 'Groups' in the top bar to create or join a group.",
                                    style = LiveCastTheme.typography.body2,
                                    color = LiveCastTheme.colors.textDisabled,
                                    textAlign = TextAlign.Center
                                )
                            } else {
                                Text(
                                    text = "No devices yet",
                                    style = LiveCastTheme.typography.h3,
                                    color = LiveCastTheme.colors.textSecondary,
                                    textAlign = TextAlign.Center
                                )

                                VerticalSpacer(8)

                                Text(
                                    text = "Phones enrolled in your groups will appear here.",
                                    style = LiveCastTheme.typography.body2,
                                    color = LiveCastTheme.colors.textDisabled,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(state.rows, key = { it.device.id }) { row ->
                            DeviceRowCard(
                                row = row,
                                onClick = { onDeviceClick(row.device) },
                                onRemove = { rowToRemove = row }
                            )
                        }
                    }
                }
            }
        }
    }

    rowToRemove?.let { row ->
        AlertDialog(
            onDismissRequest = { rowToRemove = null },
            title = {
                Text(
                    text = "Remove from group",
                    style = LiveCastTheme.typography.h3
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to remove ${row.device.displayName} from ${row.groupName}?",
                    style = LiveCastTheme.typography.body2
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.remove(row.device.id)
                        rowToRemove = null
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
                TextButton(onClick = { rowToRemove = null }) {
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
private fun DeviceRowCard(
    row: DeviceRow,
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
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                if (row.online) Color(0xFF4CAF50) else Color(0xFF9E9E9E)
                            )
                    )
                    HorizontalSpacer(8)
                    Text(
                        text = row.device.displayName,
                        style = LiveCastTheme.typography.h3,
                        color = LiveCastTheme.colors.text
                    )
                }

                VerticalSpacer(4)

                val details = if (row.groupName.isNotBlank()) {
                    "${row.device.model} • ${row.groupName}"
                } else {
                    row.device.model
                }
                Text(
                    text = details,
                    style = LiveCastTheme.typography.body2,
                    color = LiveCastTheme.colors.textSecondary
                )
            }

            if (row.canRemove) {
                HorizontalSpacer(8)
                LiveCastButton(
                    text = "Remove from group",
                    onClick = onRemove,
                    variant = LiveCastButtonVariant.Outlined
                )
            }
        }
    }
}
