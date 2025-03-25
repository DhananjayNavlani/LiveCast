package com.dhananjay.livecast.cast.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.dhananjay.livecast.cast.ui.components.lumo.components.Button

@Composable
fun LoginScreen(isSubscriber:(Boolean) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            text = "Sign In As Subscriber",
            onClick = {
                isSubscriber(true)
            }
        )

        Button(
            text = "Sign In As Broadcaster",
            onClick = {
                isSubscriber(false)
            }
        )
    }
}