package com.dhananjay.livecast.auth

import android.util.Log
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.dhananjay.livecast.ui.theme.LiveCastTheme
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch

private const val TAG = "GoogleSignIn"

// Web Client ID from Firebase Console -> google-services.json (client_type: 3)
private const val WEB_CLIENT_ID = "873835613576-rg2941cg94en9omd10resms2mmbu59h8.apps.googleusercontent.com"

/**
 * Android implementation of Google Sign-In button using Credential Manager.
 */
@Composable
actual fun GoogleSignInButton(
    onSignInResult: (idToken: String?) -> Unit,
    enabled: Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = CredentialManager.create(context)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        backgroundColor = LiveCastTheme.colors.surface,
        elevation = 4.dp
    ) {
        OutlinedButton(
            onClick = {
                scope.launch {
                    try {
                        val googleIdOption = GetGoogleIdOption.Builder()
                            .setFilterByAuthorizedAccounts(false)
                            .setServerClientId(WEB_CLIENT_ID)
                            .setAutoSelectEnabled(true)
                            .build()

                        val request = GetCredentialRequest.Builder()
                            .addCredentialOption(googleIdOption)
                            .build()

                        val result = credentialManager.getCredential(
                            request = request,
                            context = context
                        )

                        val credential = result.credential
                        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                        val idToken = googleIdTokenCredential.idToken

                        Log.d(TAG, "Google Sign-In successful")
                        onSignInResult(idToken)
                    } catch (e: GetCredentialCancellationException) {
                        Log.d(TAG, "Google Sign-In cancelled by user")
                        onSignInResult(null)
                    } catch (e: GetCredentialException) {
                        Log.e(TAG, "Google Sign-In failed", e)
                        onSignInResult(null)
                    } catch (e: Exception) {
                        Log.e(TAG, "Google Sign-In error", e)
                        onSignInResult(null)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(4.dp),
            enabled = enabled,
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                "🔵 Sign in with Google",
                style = LiveCastTheme.typography.body1,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
    }
}

