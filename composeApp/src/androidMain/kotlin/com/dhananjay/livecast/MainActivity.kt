
package com.dhananjay.livecast

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.dhananjay.livecast.cast.data.model.LiveCastUser
import com.dhananjay.livecast.cast.data.repositories.AuthRepository
import com.dhananjay.livecast.cast.data.services.AccessibilityService
import com.dhananjay.livecast.cast.ui.navigation.LiveCastNavigation
import com.dhananjay.livecast.cast.utils.Constants
import com.dhananjay.livecast.platform.getOrCreatePersistentDeviceId
import com.dhananjay.livecast.platform.initDeviceInfo
import com.dhananjay.livecast.ui.theme.LiveCastTheme
import org.koin.android.ext.android.inject
import org.koin.androidx.compose.KoinAndroidContext

/**
 * Main entry point for the Android app.
 * Uses the shared LiveCastTheme and navigation with shared Routes and LoginScreen.
 */
class MainActivity : ComponentActivity() {
    private val viewModel by inject<MainViewModel>()
    private val authRepository: AuthRepository by inject()
    private var isViewer = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize device info for unique device identification
        initDeviceInfo(applicationContext)

        setContent {
            // Using shared LiveCastTheme from commonMain
            LiveCastTheme {
                KoinAndroidContext {
                    val controller = rememberNavController()
                    val loginStatus by viewModel.getLoginStatus().collectAsStateWithLifecycle(false)
                    LiveCastNavigation(
                        controller = controller,
                        onLoginSuccess = { isViewerRole ->
                            this.isViewer = isViewerRole
                            if (isViewerRole) {
                                startService(
                                    Intent(
                                        this@MainActivity,
                                        AccessibilityService::class.java
                                    ).apply {
                                        action = Constants.ACTION_STOP_ACCESSILIBITY_SERVICE
                                    }
                                )
                            }
                            // Add user after successful login
                            if (authRepository.isLoggedIn()) {
                                val user = authRepository.getCurrentUser()!!
                                val deviceId = getOrCreatePersistentDeviceId()
                                viewModel.addUser(
                                    LiveCastUser(
                                        user.uid,
                                        user.displayName ?: "User",
                                        user.email,
                                        user.phoneNumber,
                                        user.photoUrl.toString(),
                                        isViewer = isViewerRole,
                                        widthPixels = resources.displayMetrics.widthPixels,
                                        heightPixels = resources.displayMetrics.heightPixels,
                                        deviceId = deviceId
                                    )
                                )
                            }
                        },
                        loginStatus,
                        onLogout = {
                            viewModel.logout()
                        },
                        modifier = Modifier
                            .background(LiveCastTheme.colors.primary)
                            .fillMaxSize()
                    )
                }
            }
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
