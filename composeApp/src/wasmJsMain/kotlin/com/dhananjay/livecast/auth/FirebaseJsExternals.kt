package com.dhananjay.livecast.auth

/**
 * External declarations for Firebase JS SDK.
 * These map to the Firebase JavaScript API.
 */

@JsModule("firebase/app")
@JsNonModule
external object FirebaseApp {
    fun initializeApp(config: FirebaseConfig): dynamic
    fun getApp(): dynamic
}

@JsModule("firebase/auth")
@JsNonModule
external object FirebaseAuth {
    fun getAuth(app: dynamic = definedExternally): dynamic
    fun signInWithEmailAndPassword(auth: dynamic, email: String, password: String): dynamic
    fun createUserWithEmailAndPassword(auth: dynamic, email: String, password: String): dynamic
    fun signInAnonymously(auth: dynamic): dynamic
    fun signOut(auth: dynamic): dynamic
    fun sendPasswordResetEmail(auth: dynamic, email: String): dynamic
    fun onAuthStateChanged(auth: dynamic, callback: (user: dynamic) -> Unit): dynamic
}

external interface FirebaseConfig {
    var apiKey: String
    var authDomain: String
    var projectId: String
    var storageBucket: String
    var messagingSenderId: String
    var appId: String
}

fun createFirebaseConfig(
    apiKey: String,
    authDomain: String,
    projectId: String,
    storageBucket: String,
    messagingSenderId: String,
    appId: String
): FirebaseConfig = js("""({
    apiKey: apiKey,
    authDomain: authDomain,
    projectId: projectId,
    storageBucket: storageBucket,
    messagingSenderId: messagingSenderId,
    appId: appId
})""")
