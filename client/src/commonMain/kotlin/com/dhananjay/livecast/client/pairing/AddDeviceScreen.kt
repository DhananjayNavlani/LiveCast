package com.dhananjay.livecast.client.pairing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dhananjay.livecast.ui.components.LiveCastButton
import com.dhananjay.livecast.ui.components.LiveCastButtonVariant
import com.dhananjay.livecast.ui.components.VerticalSpacer
import com.dhananjay.livecast.ui.theme.LiveCastTheme
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AddDeviceScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AddDeviceViewModel = koinViewModel(),
    onPaired: (String) -> Unit = {}
) {
    val addState by viewModel.addState.collectAsState()
    val focusManager = LocalFocusManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Add Device",
                        style = LiveCastTheme.typography.h2,
                        color = LiveCastTheme.colors.onPrimary
                    )
                },
                backgroundColor = LiveCastTheme.colors.primary,
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.reset()
                        onBack()
                    }) {
                        Text(
                            text = "←",
                            color = LiveCastTheme.colors.onPrimary,
                            style = LiveCastTheme.typography.h2
                        )
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
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (val state = addState) {
                is AddDeviceState.Entering -> {
                    Text(
                        text = "Enter Pairing Code",
                        style = LiveCastTheme.typography.h2,
                        color = LiveCastTheme.colors.text,
                        textAlign = TextAlign.Center
                    )

                    VerticalSpacer(8)

                    Text(
                        text = "Enter the 6-digit code shown on the child's device",
                        style = LiveCastTheme.typography.body2,
                        color = LiveCastTheme.colors.textSecondary,
                        textAlign = TextAlign.Center
                    )

                    VerticalSpacer(32)

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        backgroundColor = LiveCastTheme.colors.surface,
                        elevation = 4.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            OutlinedTextField(
                                value = state.code,
                                onValueChange = viewModel::onCodeChanged,
                                label = { Text("6-Digit Code") },
                                placeholder = { Text("123456") },
                                singleLine = true,
                                textStyle = TextStyle(
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 6.sp,
                                    textAlign = TextAlign.Center,
                                    color = LiveCastTheme.colors.primary
                                ),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        focusManager.clearFocus()
                                        if (state.code.length == 6) {
                                            viewModel.submit()
                                        }
                                    }
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = TextFieldDefaults.outlinedTextFieldColors(
                                    focusedBorderColor = LiveCastTheme.colors.primary,
                                    cursorColor = LiveCastTheme.colors.primary,
                                    backgroundColor = LiveCastTheme.colors.background
                                )
                            )

                            if (state.error != null) {
                                VerticalSpacer(16)
                                Text(
                                    text = state.error,
                                    color = LiveCastTheme.colors.error,
                                    style = LiveCastTheme.typography.body2,
                                    textAlign = TextAlign.Center
                                )
                            }

                            VerticalSpacer(24)

                            LiveCastButton(
                                text = "Submit",
                                onClick = {
                                    focusManager.clearFocus()
                                    viewModel.submit()
                                },
                                enabled = state.code.length == 6,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                is AddDeviceState.Submitting -> {
                    CircularProgressIndicator(
                        color = LiveCastTheme.colors.primary,
                        modifier = Modifier.size(48.dp)
                    )

                    VerticalSpacer(24)

                    Text(
                        text = "Submitting code…",
                        style = LiveCastTheme.typography.body1,
                        color = LiveCastTheme.colors.textSecondary
                    )
                }

                is AddDeviceState.Waiting -> {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(LiveCastTheme.colors.primary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = LiveCastTheme.colors.primary,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    VerticalSpacer(24)

                    Text(
                        text = "Waiting for the phone to approve…",
                        style = LiveCastTheme.typography.h2,
                        color = LiveCastTheme.colors.text,
                        textAlign = TextAlign.Center
                    )

                    VerticalSpacer(8)

                    Text(
                        text = "Please tap 'Allow' on the phone to complete pairing.",
                        style = LiveCastTheme.typography.body2,
                        color = LiveCastTheme.colors.textSecondary,
                        textAlign = TextAlign.Center
                    )

                    VerticalSpacer(32)

                    LiveCastButton(
                        text = "Cancel",
                        onClick = {
                            viewModel.reset()
                            onBack()
                        },
                        variant = LiveCastButtonVariant.Outlined
                    )
                }

                is AddDeviceState.Approved -> {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(LiveCastTheme.colors.success.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "✓",
                            style = LiveCastTheme.typography.h1,
                            color = LiveCastTheme.colors.success
                        )
                    }

                    VerticalSpacer(24)

                    Text(
                        text = "Device Paired!",
                        style = LiveCastTheme.typography.h2,
                        color = LiveCastTheme.colors.text,
                        textAlign = TextAlign.Center
                    )

                    VerticalSpacer(8)

                    Text(
                        text = "You are now supervising this device.",
                        style = LiveCastTheme.typography.body2,
                        color = LiveCastTheme.colors.textSecondary,
                        textAlign = TextAlign.Center
                    )

                    VerticalSpacer(32)

                    LiveCastButton(
                        text = "Done",
                        onClick = {
                            val devId = state.deviceId
                            viewModel.reset()
                            onPaired(devId)
                            onBack()
                        },
                        variant = LiveCastButtonVariant.Primary
                    )
                }

                is AddDeviceState.Denied -> {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(LiveCastTheme.colors.error.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "✕",
                            style = LiveCastTheme.typography.h1,
                            color = LiveCastTheme.colors.error
                        )
                    }

                    VerticalSpacer(24)

                    Text(
                        text = "Request declined",
                        style = LiveCastTheme.typography.h2,
                        color = LiveCastTheme.colors.error,
                        textAlign = TextAlign.Center
                    )

                    VerticalSpacer(8)

                    Text(
                        text = "The request was declined on the device.",
                        style = LiveCastTheme.typography.body2,
                        color = LiveCastTheme.colors.textSecondary,
                        textAlign = TextAlign.Center
                    )

                    VerticalSpacer(32)

                    LiveCastButton(
                        text = "Try Again",
                        onClick = { viewModel.reset() },
                        variant = LiveCastButtonVariant.Primary
                    )
                }

                is AddDeviceState.Expired -> {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(LiveCastTheme.colors.error.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "⏰",
                            style = LiveCastTheme.typography.h1
                        )
                    }

                    VerticalSpacer(24)

                    Text(
                        text = "Code expired before the phone approved. Ask for a new code.",
                        style = LiveCastTheme.typography.h3,
                        color = LiveCastTheme.colors.error,
                        textAlign = TextAlign.Center
                    )

                    VerticalSpacer(32)

                    LiveCastButton(
                        text = "Try Again",
                        onClick = { viewModel.reset() },
                        variant = LiveCastButtonVariant.Primary
                    )
                }
            }
        }
    }
}
