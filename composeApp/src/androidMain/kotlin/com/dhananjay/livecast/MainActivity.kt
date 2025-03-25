package com.dhananjay.livecast

import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Bundle
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.rememberNavController
import com.dhananjay.livecast.cast.data.services.AccessibilityService
import com.dhananjay.livecast.cast.ui.components.lumo.AppTheme
import com.dhananjay.livecast.cast.ui.navigation.LiveCastNavigation
import com.firebase.ui.auth.FirebaseAuthUIActivityResultContract
import org.koin.android.ext.android.inject
import org.koin.androidx.compose.KoinAndroidContext

class MainActivity : ComponentActivity() {
    private val viewModel by inject<MainViewModel>()
    private val accessibilityManager: AccessibilityManager by lazy {
        getSystemService(AccessibilityManager::class.java)
    }
    private val TAG = javaClass.simpleName
    private val signInLauncher = registerForActivityResult(FirebaseAuthUIActivityResultContract()){
        if (it.resultCode == RESULT_OK) {

        } else {
        }
    }

    private fun isAccessibilityEnabled() = accessibilityManager.getEnabledAccessibilityServiceList(
        AccessibilityServiceInfo.FEEDBACK_GENERIC
    ).any {
        it.resolveInfo.serviceInfo.run { "$packageName/$name" } == "${packageName}/${AccessibilityService::class.java.name}"
    }
    override fun onStart() {
        super.onStart()
        viewModel.addDeviceOnline()
//        if(!isAccessibilityEnabled()) {
//            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
//        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                KoinAndroidContext {
                    val controller = rememberNavController()
                    LiveCastNavigation(
                        controller = controller,
                        onSignIn = {
                            signInLauncher.launch(it)
                        },
                        modifier = Modifier.background(AppTheme.colors.primary).fillMaxSize()
                    )
                }
            }

        }
    }

    override fun onStop() {
        super.onStop()
        viewModel.removeDeviceOnline()
    }

}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}