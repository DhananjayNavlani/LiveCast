package com.dhananjay.livecast.group

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dhananjay.livecast.protocol.GroupRules
import com.dhananjay.livecast.ui.components.LiveCastButton
import com.dhananjay.livecast.ui.components.LiveCastButtonVariant
import com.dhananjay.livecast.ui.components.VerticalSpacer
import com.dhananjay.livecast.ui.theme.LiveCastTheme

@Composable
fun EnrollmentScreen(
    state: EnrollmentUiState,
    onSendVerification: () -> Unit,
    onCheckVerification: () -> Unit,
    onRetry: () -> Unit,
    onChooseGroup: (String) -> Unit,
    onBack: () -> Unit,
    onConfirm: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        when (state) {
            is EnrollmentUiState.Loading -> {
                CircularProgressIndicator()
            }

            is EnrollmentUiState.NoEmail -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = EnrollmentCopy.NO_EMAIL,
                        style = LiveCastTheme.typography.body1,
                        color = LiveCastTheme.colors.text,
                        textAlign = TextAlign.Center
                    )
                    VerticalSpacer(24)
                    LiveCastButton(
                        text = "Sign Out",
                        onClick = onSignOut,
                        variant = LiveCastButtonVariant.Secondary
                    )
                }
            }

            is EnrollmentUiState.VerifyEmail -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Verify your email",
                        style = LiveCastTheme.typography.h2,
                        color = LiveCastTheme.colors.text,
                        textAlign = TextAlign.Center
                    )
                    VerticalSpacer(8)
                    Text(
                        text = state.email,
                        style = LiveCastTheme.typography.body1,
                        color = LiveCastTheme.colors.textSecondary,
                        textAlign = TextAlign.Center
                    )
                    VerticalSpacer(16)
                    if (state.message != null) {
                        Text(
                            text = state.message,
                            style = LiveCastTheme.typography.body2,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                        VerticalSpacer(16)
                    }
                    if (state.busy) {
                        CircularProgressIndicator()
                        VerticalSpacer(16)
                    }
                    LiveCastButton(
                        text = if (state.sent) "Resend Verification Email" else "Send Verification Email",
                        onClick = onSendVerification,
                        variant = LiveCastButtonVariant.Primary,
                        modifier = Modifier.fillMaxWidth()
                    )
                    VerticalSpacer(12)
                    LiveCastButton(
                        text = "I've verified my email",
                        onClick = onCheckVerification,
                        variant = LiveCastButtonVariant.Primary,
                        modifier = Modifier.fillMaxWidth()
                    )
                    VerticalSpacer(12)
                    LiveCastButton(
                        text = "Sign Out",
                        onClick = onSignOut,
                        variant = LiveCastButtonVariant.Secondary,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            is EnrollmentUiState.NotMember -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = EnrollmentCopy.notMember(state.email),
                        style = LiveCastTheme.typography.body1,
                        color = LiveCastTheme.colors.text,
                        textAlign = TextAlign.Center
                    )
                    VerticalSpacer(24)
                    LiveCastButton(
                        text = "Check again",
                        onClick = onRetry,
                        variant = LiveCastButtonVariant.Primary,
                        modifier = Modifier.fillMaxWidth()
                    )
                    VerticalSpacer(12)
                    LiveCastButton(
                        text = "Sign Out",
                        onClick = onSignOut,
                        variant = LiveCastButtonVariant.Secondary,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            is EnrollmentUiState.ChooseGroup -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Choose a group",
                        style = LiveCastTheme.typography.h2,
                        color = LiveCastTheme.colors.text,
                        textAlign = TextAlign.Center
                    )
                    VerticalSpacer(16)
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(state.candidates) { candidate ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onChooseGroup(candidate.group.id) },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = candidate.group.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    val supNames = candidate.supervisors.map { GroupRules.displayName(it) }
                                    if (supNames.isNotEmpty()) {
                                        VerticalSpacer(4)
                                        Text(
                                            text = "Supervisors: ${supNames.joinToString(", ")}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                    VerticalSpacer(16)
                    LiveCastButton(
                        text = "Sign Out",
                        onClick = onSignOut,
                        variant = LiveCastButtonVariant.Secondary,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            is EnrollmentUiState.Confirm -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    val supNames = state.candidate.supervisors.map { GroupRules.displayName(it) }
                    Text(
                        text = EnrollmentCopy.confirm(state.candidate.group.name, supNames),
                        style = LiveCastTheme.typography.body1,
                        color = LiveCastTheme.colors.text,
                        textAlign = TextAlign.Center
                    )
                    if (state.error != null) {
                        VerticalSpacer(16)
                        Text(
                            text = state.error,
                            style = LiveCastTheme.typography.body2,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    }
                    if (state.busy) {
                        VerticalSpacer(16)
                        CircularProgressIndicator()
                    }
                    VerticalSpacer(24)
                    LiveCastButton(
                        text = "Continue",
                        onClick = onConfirm,
                        variant = LiveCastButtonVariant.Primary,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (state.canGoBack) {
                        VerticalSpacer(12)
                        LiveCastButton(
                            text = "Back",
                            onClick = onBack,
                            variant = LiveCastButtonVariant.Secondary,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    VerticalSpacer(12)
                    LiveCastButton(
                        text = "Sign Out",
                        onClick = onSignOut,
                        variant = LiveCastButtonVariant.Secondary,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            is EnrollmentUiState.Enrolled -> {
                CircularProgressIndicator()
            }

            is EnrollmentUiState.Error -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = state.message,
                        style = LiveCastTheme.typography.body1,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                    VerticalSpacer(24)
                    LiveCastButton(
                        text = "Check again",
                        onClick = onRetry,
                        variant = LiveCastButtonVariant.Primary,
                        modifier = Modifier.fillMaxWidth()
                    )
                    VerticalSpacer(12)
                    LiveCastButton(
                        text = "Sign Out",
                        onClick = onSignOut,
                        variant = LiveCastButtonVariant.Secondary,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
