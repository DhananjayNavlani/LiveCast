
package com.dhananjay.livecast.cast.ui.navigation

import android.content.Intent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.dhananjay.livecast.cast.data.RemoteDataSource
import com.dhananjay.livecast.cast.ui.stage.StageScreen
import com.dhananjay.livecast.cast.ui.video.VideoScreenActivity
import com.dhananjay.livecast.cast.utils.Constants
import com.dhananjay.livecast.ui.navigation.Routes
import com.dhananjay.livecast.ui.screens.LoginScreen
import com.dhananjay.livecast.ui.screens.UserRole
import com.firebase.ui.auth.AuthUI
import org.koin.compose.koinInject
import kotlin.random.Random

/**
 * Android-specific navigation graph that uses the shared Routes and LoginScreen
 * from commonMain while providing Android-specific functionality like Firebase Auth.
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
                StageScreen(
                    state = state,
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
        composable<Routes.LoginScreen> {
            // Use the shared LoginScreen from commonMain
            LoginScreen(
                onRoleSelected = { role ->
                    // Convert UserRole to isViewer boolean for the existing sign-in flow
                    val isViewer = when (role) {
                        is UserRole.Subscriber -> true
                        is UserRole.Broadcaster -> false
                    }
                    
                    // Create Firebase Auth intent
                    val intent = AuthUI.getInstance()
                        .createSignInIntentBuilder()
                        .setAvailableProviders(
                            listOf(
                                AuthUI.IdpConfig.GoogleBuilder().build(),
                                AuthUI.IdpConfig.EmailBuilder().build(),
                                AuthUI.IdpConfig.GitHubBuilder()
                                    .setCustomParameters(mapOf())
                                    .build(),
                                AuthUI.IdpConfig.TwitterBuilder()
                                    .setCustomParameters(mapOf())
                                    .build()
                            )
                        )
                        .build()
                    onSignIn(intent, isViewer)
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
