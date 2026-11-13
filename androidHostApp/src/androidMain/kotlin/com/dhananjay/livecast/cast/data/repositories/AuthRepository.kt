package com.dhananjay.livecast.cast.data.repositories

import com.google.firebase.auth.FirebaseAuth

class AuthRepository(
    private val auth: FirebaseAuth
) {
    fun getCurrentUser() = auth.currentUser

    fun signOut() {
        auth.signOut()
    }

    fun isLoggedIn() = auth.currentUser != null
}