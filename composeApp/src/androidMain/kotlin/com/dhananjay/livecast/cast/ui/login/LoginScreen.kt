package com.dhananjay.livecast.cast.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.dhananjay.livecast.cast.ui.components.lumo.components.Button
import com.dhananjay.livecast.cast.ui.components.lumo.components.ButtonVariant

@Composable
fun LoginScreen(isViewer:(Boolean) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            text = "Sign In As Subscriber",
            variant = ButtonVariant.SecondaryElevated,
            onClick = {
                isViewer(true)
            }
        )

        Button(
            text = "Sign In As Broadcaster",
            variant = ButtonVariant.SecondaryElevated,
            onClick = {
                isViewer(false)
            }
        )
    }
}