package com.dhananjay.livecast.client.group

import androidx.compose.foundation.background
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
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.IconButton
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dhananjay.livecast.ui.components.LiveCastButton
import com.dhananjay.livecast.ui.components.LiveCastButtonVariant
import com.dhananjay.livecast.ui.components.VerticalSpacer
import com.dhananjay.livecast.ui.theme.LiveCastTheme
import kotlinx.datetime.TimeZone
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun DeviceDetailScreen(
    deviceId: String,
    groupId: String,
    deviceName: String = "",
    onBack: () -> Unit,
    onOpenViewer: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DeviceDetailViewModel = koinViewModel(
        parameters = { parametersOf(deviceId, groupId) }
    ),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "App activity",
                        style = LiveCastTheme.typography.h2,
                        color = LiveCastTheme.colors.onTopBar
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text(
                            text = "←",
                            color = LiveCastTheme.colors.onTopBar,
                            style = LiveCastTheme.typography.h2
                        )
                    }
                },
                backgroundColor = LiveCastTheme.colors.topBar,
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
            if (deviceName.isNotBlank()) {
                Text(
                    text = deviceName,
                    style = LiveCastTheme.typography.h3,
                    color = LiveCastTheme.colors.text
                )
                VerticalSpacer(12)
            }

            LiveCastButton(
                text = "View screen",
                onClick = onOpenViewer,
                variant = LiveCastButtonVariant.Primary
            )

            VerticalSpacer(24)

            when {
                state.loading -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = LiveCastTheme.colors.primary)
                    }
                }

                state.error != null -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = state.error ?: "",
                            color = LiveCastTheme.colors.error,
                            style = LiveCastTheme.typography.body1,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                state.events.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(LiveCastTheme.colors.primary.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "📋",
                                    style = LiveCastTheme.typography.h1
                                )
                            }
                            VerticalSpacer(16)
                            Text(
                                text = "No app changes yet",
                                style = LiveCastTheme.typography.h3,
                                color = LiveCastTheme.colors.textSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.events, key = { it.id }) { event ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                backgroundColor = LiveCastTheme.colors.surface,
                                elevation = 1.dp
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = AppEventCopy.line(event, TimeZone.currentSystemDefault()),
                                        style = LiveCastTheme.typography.body1,
                                        color = LiveCastTheme.colors.text
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
