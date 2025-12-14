package com.dhananjay.livecast

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.dhananjay.livecast.auth.authModule
import org.koin.core.context.startKoin

fun main() {
    startKoin {
        modules(authModule)
    }

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "LiveCast",
        ) {
            App()
        }
    }
}