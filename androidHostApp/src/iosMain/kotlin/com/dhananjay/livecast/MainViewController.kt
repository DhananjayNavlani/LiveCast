package com.dhananjay.livecast

import androidx.compose.ui.window.ComposeUIViewController
import com.dhananjay.livecast.auth.authModule
import org.koin.core.context.startKoin

fun initKoin() {
    startKoin {
        modules(authModule)
    }
}

fun MainViewController() = ComposeUIViewController {
    App()
}
