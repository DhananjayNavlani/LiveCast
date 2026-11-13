package com.dhananjay.livecast.client.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.dhananjay.livecast.auth.authModule
import com.dhananjay.livecast.client.ui.ClientRoot
import org.koin.core.context.startKoin

fun main() {
    startKoin {
        modules(authModule)
    }

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "LiveCast Client"
        ) {
            ClientRoot()
        }
    }
}
