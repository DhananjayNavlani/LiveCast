package com.dhananjay.livecast.client.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    data class DeviceDetail(val device: HostDevice) : ClientScreen
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
                            onDeviceClick = { device -> currentScreen = ClientScreen.DeviceDetail(device) },
                            onSignOut = { viewModel.signOut() }
                        )
                    }

                    is ClientScreen.GroupManagement -> {
                        GroupManagementScreen(
                            onBack = { currentScreen = ClientScreen.MyDevices }
                        )
                    }

                    is ClientScreen.DeviceDetail -> {
                        DeviceDetailScreen(
                            deviceId = screen.device.id,
                            groupId = screen.device.groupId ?: "",
                            deviceName = screen.device.displayName,
                            onBack = { currentScreen = ClientScreen.MyDevices },
                            onOpenViewer = { currentScreen = ClientScreen.Viewer(screen.device) }
                        )
                    }

                    is ClientScreen.Viewer -> {
                        ViewerScreen(
                            viewModel = viewModel,
                            onBack = { currentScreen = ClientScreen.DeviceDetail(screen.device) }
                        )
                    }
                }
            }
        }
    }
}
