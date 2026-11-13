
package com.dhananjay.livecast.cast.ui.navigation

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.dhananjay.livecast.cast.data.RemoteDataSource
import com.dhananjay.livecast.cast.ui.stage.StageScreen
import com.dhananjay.livecast.cast.ui.video.VideoScreenActivity
import com.dhananjay.livecast.cast.utils.Constants
import com.dhananjay.livecast.pairing.PairSupervisorScreen
import com.dhananjay.livecast.pairing.SupervisedByViewModel
import com.dhananjay.livecast.ui.components.LiveCastButton
import com.dhananjay.livecast.ui.components.LiveCastButtonVariant
import com.dhananjay.livecast.ui.components.VerticalSpacer
import com.dhananjay.livecast.ui.navigation.Routes
import com.dhananjay.livecast.ui.theme.LiveCastTheme
import com.firebase.ui.auth.AuthUI
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import kotlin.random.Random

/**
 * Android-specific navigation graph that uses the shared Routes
 * while providing Android-specific functionality like Firebase Auth.
 */
@Composable
fun LiveCastNavigation(
    controller: NavHostController,
    onSignIn: (Intent, Boolean) -> Unit,
    loginStatus: Boolean,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    NavHost(
        navController = controller,
        startDestination = if (loginStatus) Routes.StageScreen else Routes.LoginScreen,
        modifier = modifier
    ) {
        composable<Routes.StageScreen> {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color(Random.nextFloat(), Random.nextFloat(), Random.nextFloat())
            ) {
                val state by koinInject<RemoteDataSource>().devicesOnline.collectAsStateWithLifecycle(
                    emptyList()
                )
                val supervisedByViewModel: SupervisedByViewModel = koinViewModel()
                val supervisorNames by supervisedByViewModel.supervisorNames.collectAsStateWithLifecycle()

                StageScreen(
                    state = state,
                    supervisorNames = supervisorNames,
                    onPairSupervisor = {
                        controller.navigate(Routes.PairSupervisorScreen)
                    },
                    onStart = {
                        context.startActivity(
                            Intent(
                                context,
                                VideoScreenActivity::class.java
                            ).apply {
                                putExtra(Constants.EXTRA_IS_VIEWER, true)
                            }
                        )
                    },
                    onLogout = {
                        onLogout()
                        controller.navigate(Routes.LoginScreen) {
                            popUpTo(Routes.StageScreen) {
                                inclusive = true
                            }
                        }
                    }
                )
            }
        }
        composable<Routes.PairSupervisorScreen> {
            PairSupervisorScreen(
                onBack = { controller.popBackStack() }
            )
        }
        composable<Routes.LoginScreen> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Welcome to LiveCast",
                    style = LiveCastTheme.typography.h1,
                    color = LiveCastTheme.colors.text,
                    textAlign = TextAlign.Center
                )

                VerticalSpacer(8)

                Text(
                    text = "Sign in to broadcast your screen",
                    style = LiveCastTheme.typography.body1,
                    color = LiveCastTheme.colors.textSecondary,
                    textAlign = TextAlign.Center
                )

                VerticalSpacer(32)

                LiveCastButton(
                    text = "Sign In As Broadcaster",
                    onClick = {
                        val intent = AuthUI.getInstance()
                            .createSignInIntentBuilder()
                            .setAvailableProviders(
                                listOf(
                                    AuthUI.IdpConfig.GoogleBuilder().build(),
                                    AuthUI.IdpConfig.EmailBuilder().build(),
                                    AuthUI.IdpConfig.AnonymousBuilder().build()
                                )
                            )
                            .build()
                        onSignIn(intent, false)
                    },
                    variant = LiveCastButtonVariant.Primary,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
