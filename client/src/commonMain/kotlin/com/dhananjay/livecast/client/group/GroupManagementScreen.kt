package com.dhananjay.livecast.client.group

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.IconButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TextFieldDefaults
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
import com.dhananjay.livecast.protocol.GroupMember
import com.dhananjay.livecast.protocol.GroupRole
import com.dhananjay.livecast.protocol.GroupRules
import com.dhananjay.livecast.ui.components.HorizontalSpacer
import com.dhananjay.livecast.ui.components.LiveCastButton
import com.dhananjay.livecast.ui.components.LiveCastButtonVariant
import com.dhananjay.livecast.ui.components.VerticalSpacer
import com.dhananjay.livecast.ui.theme.LiveCastTheme
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun GroupManagementScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GroupManagementViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()

    var newGroupName by remember { mutableStateOf("") }
    var newMemberEmail by remember { mutableStateOf("") }
    var newMemberName by remember { mutableStateOf("") }
    var newMemberRole by remember { mutableStateOf(GroupRole.DEVICE) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Group Management",
                        style = LiveCastTheme.typography.h2,
                        color = LiveCastTheme.colors.onPrimary
                    )
                },
                backgroundColor = LiveCastTheme.colors.primary,
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                state.error?.let { err ->
                    Text(
                        text = err,
                        color = LiveCastTheme.colors.error,
                        style = LiveCastTheme.typography.body2,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )
                }
            }

            // Create Group Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    backgroundColor = LiveCastTheme.colors.surface,
                    elevation = 2.dp
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Create a New Group",
                            style = LiveCastTheme.typography.h3,
                            color = LiveCastTheme.colors.text
                        )
                        VerticalSpacer(8)
                        OutlinedTextField(
                            value = newGroupName,
                            onValueChange = { newGroupName = it },
                            label = { Text("Group Name") },
                            placeholder = { Text("e.g. Family") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                focusedBorderColor = LiveCastTheme.colors.primary,
                                cursorColor = LiveCastTheme.colors.primary
                            )
                        )
                        VerticalSpacer(12)
                        LiveCastButton(
                            text = "Create Group",
                            onClick = {
                                viewModel.createGroup(newGroupName)
                                newGroupName = ""
                            },
                            enabled = !state.busy && newGroupName.isNotBlank(),
                            variant = LiveCastButtonVariant.Primary,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Groups selection
            if (state.memberships.isEmpty()) {
                item {
                    Text(
                        text = "You are not a member of any group yet.",
                        style = LiveCastTheme.typography.body1,
                        color = LiveCastTheme.colors.textSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(16.dp)
                    )
                }
            } else {
                item {
                    Column {
                        Text(
                            text = "Your Groups",
                            style = LiveCastTheme.typography.h3,
                            color = LiveCastTheme.colors.text
                        )
                        VerticalSpacer(8)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            state.memberships.forEach { membership ->
                                val isSelected = membership.group.id == state.selectedGroupId
                                val bgColor = if (isSelected) LiveCastTheme.colors.primary else LiveCastTheme.colors.surface
                                val textColor = if (isSelected) LiveCastTheme.colors.onPrimary else LiveCastTheme.colors.text

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(bgColor)
                                        .clickable { viewModel.selectGroup(membership.group.id) }
                                        .padding(horizontal = 16.dp, vertical = 10.dp)
                                ) {
                                    Text(
                                        text = "${membership.group.name} (${membership.myRole.name.lowercase()})",
                                        color = textColor,
                                        style = LiveCastTheme.typography.body2
                                    )
                                }
                            }
                        }
                    }
                }

                // Add Member Section (only if canManage)
                if (state.canManage) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            backgroundColor = LiveCastTheme.colors.surface,
                            elevation = 2.dp
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Add Member to Group",
                                    style = LiveCastTheme.typography.h3,
                                    color = LiveCastTheme.colors.text
                                )
                                VerticalSpacer(8)
                                OutlinedTextField(
                                    value = newMemberEmail,
                                    onValueChange = { newMemberEmail = it },
                                    label = { Text("Member Email") },
                                    placeholder = { Text("user@example.com") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = TextFieldDefaults.outlinedTextFieldColors(
                                        focusedBorderColor = LiveCastTheme.colors.primary,
                                        cursorColor = LiveCastTheme.colors.primary
                                    )
                                )
                                VerticalSpacer(8)
                                OutlinedTextField(
                                    value = newMemberName,
                                    onValueChange = { newMemberName = it },
                                    label = { Text("Display Name (optional)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = TextFieldDefaults.outlinedTextFieldColors(
                                        focusedBorderColor = LiveCastTheme.colors.primary,
                                        cursorColor = LiveCastTheme.colors.primary
                                    )
                                )
                                VerticalSpacer(8)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Role:",
                                        style = LiveCastTheme.typography.body2,
                                        color = LiveCastTheme.colors.text
                                    )
                                    LiveCastButton(
                                        text = "Device",
                                        onClick = { newMemberRole = GroupRole.DEVICE },
                                        variant = if (newMemberRole == GroupRole.DEVICE) LiveCastButtonVariant.Primary else LiveCastButtonVariant.Outlined
                                    )
                                    LiveCastButton(
                                        text = "Supervisor",
                                        onClick = { newMemberRole = GroupRole.SUPERVISOR },
                                        variant = if (newMemberRole == GroupRole.SUPERVISOR) LiveCastButtonVariant.Primary else LiveCastButtonVariant.Outlined
                                    )
                                }
                                VerticalSpacer(12)
                                LiveCastButton(
                                    text = "Add Member",
                                    onClick = {
                                        viewModel.addMember(newMemberEmail, newMemberRole, newMemberName)
                                        newMemberEmail = ""
                                        newMemberName = ""
                                    },
                                    enabled = !state.busy && newMemberEmail.isNotBlank(),
                                    variant = LiveCastButtonVariant.Primary,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                // Members List Header
                item {
                    Text(
                        text = "Members",
                        style = LiveCastTheme.typography.h3,
                        color = LiveCastTheme.colors.text
                    )
                }

                // Member Items
                items(state.members, key = { it.email }) { member ->
                    MemberRowCard(
                        member = member,
                        canManage = state.canManage,
                        myEmail = state.myEmail,
                        onChangeRole = { newRole -> viewModel.changeRole(member.email, newRole) },
                        onRemove = { viewModel.removeMember(member.email) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MemberRowCard(
    member: GroupMember,
    canManage: Boolean,
    myEmail: String?,
    onChangeRole: (GroupRole) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isSelf = myEmail != null && GroupRules.normalizeEmail(member.email) == GroupRules.normalizeEmail(myEmail)

    Card(
        modifier = modifier.fillMaxWidth(),
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = GroupRules.displayName(member),
                    style = LiveCastTheme.typography.h3,
                    color = LiveCastTheme.colors.text
                )
                VerticalSpacer(4)
                Text(
                    text = "${member.email} • ${member.role.name.lowercase()}",
                    style = LiveCastTheme.typography.body2,
                    color = LiveCastTheme.colors.textSecondary
                )
            }

            if (canManage && !isSelf) {
                HorizontalSpacer(8)
                if (member.role == GroupRole.SUPERVISOR) {
                    LiveCastButton(
                        text = "Make device",
                        onClick = { onChangeRole(GroupRole.DEVICE) },
                        variant = LiveCastButtonVariant.Outlined
                    )
                } else if (member.role == GroupRole.DEVICE) {
                    LiveCastButton(
                        text = "Make supervisor",
                        onClick = { onChangeRole(GroupRole.SUPERVISOR) },
                        variant = LiveCastButtonVariant.Outlined
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
}
