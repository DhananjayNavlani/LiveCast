package com.dhananjay.livecast.client.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.dhananjay.livecast.client.ui.ClientRoot

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "LiveCast Client"
    ) {
        ClientRoot()
    }
}
