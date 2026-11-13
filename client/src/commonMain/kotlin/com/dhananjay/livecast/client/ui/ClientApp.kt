package com.dhananjay.livecast.client.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.dhananjay.livecast.auth.LoginViewModel
import com.dhananjay.livecast.ui.theme.LiveCastTheme
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ClientApp(
    viewModel: LoginViewModel = koinViewModel()
) {
    LiveCastTheme {
        val uiState by viewModel.uiState.collectAsState()

        if (uiState.isLoggedIn) {
            ViewerScreen(viewModel = viewModel)
        } else {
            ClientLoginScreen(viewModel = viewModel)
        }
    }
}
