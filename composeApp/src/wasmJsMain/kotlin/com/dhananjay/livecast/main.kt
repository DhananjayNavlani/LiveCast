package com.dhananjay.livecast

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.dhananjay.livecast.auth.authModule
import kotlinx.browser.document
import org.koin.core.context.startKoin

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    startKoin {
        modules(authModule)
    }

    ComposeViewport(document.body!!) {
        App()
    }
}