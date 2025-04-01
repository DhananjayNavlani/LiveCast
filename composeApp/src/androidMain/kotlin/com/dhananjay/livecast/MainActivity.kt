package com.dhananjay.livecast

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
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
import com.dhananjay.livecast.cast.ui.components.lumo.AppTheme
import com.dhananjay.livecast.cast.ui.navigation.LiveCastNavigation
import com.dhananjay.livecast.cast.utils.Constants
import com.firebase.ui.auth.FirebaseAuthUIActivityResultContract
import org.koin.android.ext.android.inject
import org.koin.androidx.compose.KoinAndroidContext

class MainActivity : ComponentActivity() {
    private val viewModel by inject<MainViewModel>()
    private val authRepository: AuthRepository by inject()
    private var isViewer = false
    private val TAG = javaClass.simpleName
    private val signInLauncher = registerForActivityResult(FirebaseAuthUIActivityResultContract()){
        if (it.resultCode == RESULT_OK) {
            Log.d(TAG, "The login response is : ${it.idpResponse}")
            if(!authRepository.isLoggedIn()){
                Toast.makeText(this, "Login failed Try again", Toast.LENGTH_SHORT).show()
                return@registerForActivityResult
            }
            val user = authRepository.getCurrentUser()!!
            viewModel.addUser(LiveCastUser(
                user.uid,
                user.displayName ?: "User",
                user.email,
                user.phoneNumber,
                user.photoUrl.toString(),
                isViewer = isViewer,
                widthPixels = resources.displayMetrics.widthPixels,
                heightPixels = resources.displayMetrics.heightPixels,
            ))
        } else{
            Toast.makeText(this, "Something went wrong", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                KoinAndroidContext {
                    val controller = rememberNavController()
                    val loginStatus by viewModel.getLoginStatus().collectAsStateWithLifecycle(false)
                    LiveCastNavigation(
                        controller = controller,
                        onSignIn = { intent, isViewer ->
                            this.isViewer = isViewer
                            if(isViewer){
                                startService(Intent(this@MainActivity, AccessibilityService::class.java).apply {
                                    action = Constants.ACTION_STOP_ACCESSILIBITY_SERVICE
                                })
                            }
                            signInLauncher.launch(intent)
                        }
                        ,
                        loginStatus,
                        onLogout = {
                            viewModel.logout()
                        },
                        modifier = Modifier
                            .background(AppTheme.colors.primary)
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