package com.dhananjay.livecast.client.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.dhananjay.livecast.auth.LoginViewModel
import com.dhananjay.livecast.client.group.ClientGate
import com.dhananjay.livecast.client.group.DeviceDetailScreen
import com.dhananjay.livecast.client.group.GroupManagementScreen
import com.dhananjay.livecast.client.group.MyDevicesScreen
import com.dhananjay.livecast.client.group.VerifyEmailScreen
import com.dhananjay.livecast.client.group.clientGate
import com.dhananjay.livecast.protocol.HostDevice
import com.dhananjay.livecast.ui.theme.LiveCastTheme
import org.koin.compose.viewmodel.koinViewModel

private sealed interface ClientScreen {
    data object MyDevices : ClientScreen
    data object GroupManagement : ClientScreen
    data class DeviceDetail(val device: HostDevice, val groupId: String) : ClientScreen
    data class Viewer(val device: HostDevice) : ClientScreen
}

@Composable
fun ClientApp(
    viewModel: LoginViewModel = koinViewModel()
) {
    LiveCastTheme {
        val uiState by viewModel.uiState.collectAsState()
        val gate = clientGate(uiState.user)

        when (gate) {
            ClientGate.SIGN_IN -> {
                ClientLoginScreen(viewModel = viewModel)
            }

            ClientGate.GUEST -> {
                MyDevicesScreen(
                    onManageGroups = {},
                    onDeviceClick = {},
                    onSignOut = { viewModel.signOut() }
                )
            }

            ClientGate.VERIFY_EMAIL -> {
                VerifyEmailScreen(
                    onSignOut = { viewModel.signOut() }
                )
            }

            ClientGate.READY -> {
                var currentScreen by remember { mutableStateOf<ClientScreen>(ClientScreen.MyDevices) }

                when (val screen = currentScreen) {
                    is ClientScreen.MyDevices -> {
                        MyDevicesScreen(
                            onManageGroups = { currentScreen = ClientScreen.GroupManagement },
                            onDeviceClick = { device ->
                                val groupId = device.groupId
                                if (groupId != null) {
                                    currentScreen = ClientScreen.DeviceDetail(device, groupId)
                                }
                            },
                            onSignOut = { viewModel.signOut() }
                        )
                    }

                    is ClientScreen.GroupManagement -> {
                        GroupManagementScreen(
                            onBack = { currentScreen = ClientScreen.MyDevices }
                        )
                    }

                    is ClientScreen.DeviceDetail -> {
                        val uid = uiState.user?.id ?: ""
                        CompositionLocalProvider(
                            LocalViewModelStoreOwner provides rememberScopedViewModelStoreOwner(uid, screen.device.id, screen.groupId)
                        ) {
                            DeviceDetailScreen(
                                deviceId = screen.device.id,
                                groupId = screen.groupId,
                                deviceName = screen.device.displayName,
                                onBack = { currentScreen = ClientScreen.MyDevices },
                                onOpenViewer = { currentScreen = ClientScreen.Viewer(screen.device) }
                            )
                        }
                    }

                    is ClientScreen.Viewer -> {
                        ViewerScreen(
                            viewModel = viewModel,
                            onBack = {
                                val groupId = screen.device.groupId
                                if (groupId != null) {
                                    currentScreen = ClientScreen.DeviceDetail(screen.device, groupId)
                                } else {
                                    currentScreen = ClientScreen.MyDevices
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
