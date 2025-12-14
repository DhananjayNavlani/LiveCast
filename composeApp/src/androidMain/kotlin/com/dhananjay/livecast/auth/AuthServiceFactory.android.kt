package com.dhananjay.livecast.auth

import com.google.firebase.auth.FirebaseAuth

actual fun createAuthService(): AuthService {
    return AndroidFirebaseAuthService(FirebaseAuth.getInstance())
}
