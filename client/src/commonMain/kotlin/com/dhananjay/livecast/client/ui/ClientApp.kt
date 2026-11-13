package com.dhananjay.livecast.client.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.dhananjay.livecast.auth.LoginViewModel
import com.dhananjay.livecast.client.pairing.AddDeviceScreen
import com.dhananjay.livecast.client.pairing.MyDevicesScreen
import com.dhananjay.livecast.protocol.HostDevice
import com.dhananjay.livecast.ui.theme.LiveCastTheme
import org.koin.compose.viewmodel.koinViewModel

private sealed interface ClientScreen {
    data object MyDevices : ClientScreen
    data object AddDevice : ClientScreen
    data class Viewer(val device: HostDevice) : ClientScreen
}

@Composable
fun ClientApp(
    viewModel: LoginViewModel = koinViewModel()
) {
    LiveCastTheme {
        val uiState by viewModel.uiState.collectAsState()

        if (uiState.isLoggedIn) {
            var currentScreen by remember { mutableStateOf<ClientScreen>(ClientScreen.MyDevices) }

            when (val screen = currentScreen) {
                is ClientScreen.MyDevices -> {
                    MyDevicesScreen(
                        onAddDevice = { currentScreen = ClientScreen.AddDevice },
                        onDeviceClick = { device -> currentScreen = ClientScreen.Viewer(device) },
                        onSignOut = { viewModel.signOut() }
                    )
                }

                is ClientScreen.AddDevice -> {
                    AddDeviceScreen(
                        onBack = { currentScreen = ClientScreen.MyDevices },
                        onPaired = { currentScreen = ClientScreen.MyDevices }
                    )
                }

                is ClientScreen.Viewer -> {
                    ViewerScreen(
                        viewModel = viewModel,
                        onBack = { currentScreen = ClientScreen.MyDevices }
                    )
                }
            }
        } else {
            ClientLoginScreen(viewModel = viewModel)
        }
    }
}
