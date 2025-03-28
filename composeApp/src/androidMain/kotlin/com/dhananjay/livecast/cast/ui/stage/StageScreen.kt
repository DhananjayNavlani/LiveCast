package com.dhananjay.livecast.cast.ui.stage

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.dhananjay.livecast.cast.data.PermissionManager
import com.dhananjay.livecast.cast.data.model.LiveCastUser
import com.dhananjay.livecast.cast.data.repositories.PreferencesRepository
import livecast.composeapp.generated.resources.Res
import livecast.composeapp.generated.resources.compose_multiplatform
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject


@Composable
fun StageScreen(
    state: List<LiveCastUser>,
    onStart: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    preferencesRepository: PreferencesRepository = koinInject(),
    permissionManager: PermissionManager = koinInject(),
) {

    val user by preferencesRepository.getUser().collectAsStateWithLifecycle(null)
    Box(modifier = modifier.fillMaxSize()) {

/*        val text = when (state) {
            WebRTCSessionState.Offline -> {
                enabledCall = false
                stringResource(id = R.string.button_start_session)
            }
            WebRTCSessionState.Impossible -> {
                enabledCall = false
                stringResource(id = R.string.session_impossible)
            }
            WebRTCSessionState.Ready -> {
                enabledCall = true
                stringResource(id = R.string.session_ready)
            }
            WebRTCSessionState.Creating -> {
                enabledCall = true
                stringResource(id = R.string.session_creating)
            }
            WebRTCSessionState.Active -> {
                enabledCall = false
                stringResource(id = R.string.session_active)
            }
        }

        Button(
            modifier = Modifier.align(Alignment.Center),
            enabled = enabledCall,
            onClick = { onJoinCall.invoke() }
        ) {
            Text(
                text = text,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )
        }*/
        IconButton(
            onClick = onLogout,
            modifier = modifier.align(Alignment.TopEnd)
        ) {
            Icon(Icons.AutoMirrored.Filled.ExitToApp, null)
        }

        Column(
            modifier = modifier.align(Alignment.TopCenter),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AsyncImage(
                model = user?.photoUrl,
                contentDescription = null,
                placeholder = painterResource(Res.drawable.compose_multiplatform),
                modifier = modifier
                    .align(Alignment.CenterHorizontally)
                    .fillMaxSize(0.2f)
            )
            Text("Welcome ${user?.name}")

        }

        if (user?.isViewer == true) {
            Column(
                modifier = modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Online Users: [${state.size}]")
                LazyColumn {
                    items(state) { user ->
                        Text(user.name)
                    }
                }

                Button(
                    onClick = {
                        onStart()
                    },
                ) {
                    Text(text = "Start Session")
                }
            }
        } else {
            val context = LocalContext.current
            Column(
                modifier = modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val isEnabled = permissionManager.isAccessibilityEnabled()

                Text("Accessibility Service is ${if (isEnabled) "enabled" else "disabled"}")
                if (!isEnabled) {
                    Button(
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        },
                    ) {
                        Text(text = "Enable Accessibility Service")
                    }
                }
            }
        }

    }

}