# Firebase Authentication Setup for Desktop and Web

This guide explains how to configure and use Firebase Authentication for Desktop (JVM) and Web (WasmJS) platforms.

## Overview

- **Desktop (JVM)**: Uses Firebase Auth REST API for real authentication
- **Web (WasmJS)**: Uses Firebase JS SDK for client-side authentication

## Desktop Setup

The Desktop implementation uses Firebase Authentication REST API to connect to your Firebase project.

### Configuration

You need to provide your Firebase API key in one of two ways:

#### Option 1: Environment Variable (Recommended)
Set the `FIREBASE_API_KEY` environment variable:

```bash
export FIREBASE_API_KEY="your-firebase-api-key"
./gradlew :composeApp:run
```

#### Option 2: Configuration File
Create a configuration file at `~/.livecast/config.properties`:

```properties
firebase_api_key=your-firebase-api-key
```

### Getting Your Firebase API Key

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Select your project
3. Click on Project Settings (gear icon)
4. Scroll down to "Your apps" section
5. Copy the "Web API Key"

### Features

The desktop implementation supports:
- ✅ Email/Password sign in
- ✅ Email/Password sign up
- ✅ Anonymous sign in
- ✅ Password reset email
- ✅ Sign out
- ✅ Persistent sessions (saved to `~/.livecast/auth.properties`)

### Running Desktop App

```bash
./gradlew :composeApp:run
```

## Web (WasmJS) Setup

The Web implementation uses Firebase JS SDK for authentication.

### Configuration

Firebase configuration can be provided in two ways:

#### Option 1: Via Code (Recommended for Production)
In your web entry point, initialize Firebase before using auth:

```kotlin
import com.dhananjay.livecast.auth.WasmAuthService

val authService = WasmAuthService()
authService.initializeFirebase(
    apiKey = "your-api-key",
    authDomain = "your-project-id.firebaseapp.com",
    projectId = "your-project-id",
    storageBucket = "your-project-id.appspot.com",
    messagingSenderId = "your-sender-id",
    appId = "your-app-id"
)
```

#### Option 2: Via Browser localStorage (Development)
Set these keys in browser localStorage (F12 → Console):

```javascript
localStorage.setItem('firebase_config_api_key', 'your-api-key');
localStorage.setItem('firebase_config_auth_domain', 'your-project-id.firebaseapp.com');
localStorage.setItem('firebase_config_project_id', 'your-project-id');
localStorage.setItem('firebase_config_storage_bucket', 'your-project-id.appspot.com');
localStorage.setItem('firebase_config_messaging_sender_id', 'your-sender-id');
localStorage.setItem('firebase_config_app_id', 'your-app-id');
```

### Getting Firebase Web Configuration

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Select your project
3. Click on Project Settings (gear icon)
4. Scroll to "Your apps" section
5. Add a Web app if you haven't already
6. Copy the Firebase configuration object

### Features

The web implementation supports:
- ✅ Email/Password sign in
- ✅ Email/Password sign up
- ✅ Anonymous sign in
- ✅ Password reset email
- ✅ Sign out
- ✅ Auth state listener
- ✅ Automatic session management via Firebase JS SDK

### Adding Firebase JS SDK

The Firebase JS SDK needs to be included in your HTML file. Add this to `composeApp/src/wasmJsMain/resources/index.html`:

```html
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>LiveCast</title>
    <script src="composeApp.js"></script>
    
    <!-- Firebase JS SDK -->
    <script type="module">
        import { initializeApp } from 'https://www.gstatic.com/firebasejs/10.7.1/firebase-app.js';
        import { getAuth, signInWithEmailAndPassword, createUserWithEmailAndPassword, 
                 signInAnonymously, signOut, sendPasswordResetEmail, onAuthStateChanged } 
        from 'https://www.gstatic.com/firebasejs/10.7.1/firebase-auth.js';
        
        // Make Firebase available globally for Kotlin/Wasm
        window.FirebaseApp = { initializeApp };
        window.FirebaseAuth = { 
            getAuth, 
            signInWithEmailAndPassword, 
            createUserWithEmailAndPassword,
            signInAnonymously, 
            signOut, 
            sendPasswordResetEmail, 
            onAuthStateChanged 
        };
    </script>
</head>
<body>
    <div id="root"></div>
    <script src="composeApp.js"></script>
</body>
</html>
```

### Running Web App

```bash
./gradlew :composeApp:wasmJsBrowserDevelopmentRun
```

Then open http://localhost:8080 in your browser.

## Firebase Project Setup

### Enable Authentication Methods

1. Go to Firebase Console → Authentication → Sign-in method
2. Enable the following providers:
   - **Email/Password**: Click "Email/Password" and toggle "Enable"
   - **Anonymous**: Click "Anonymous" and toggle "Enable"

### Security Rules (Optional)

For Firestore or Realtime Database, update security rules to allow authenticated access:

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /{document=**} {
      allow read, write: if request.auth != null;
    }
  }
}
```

## Troubleshooting

### Desktop

**Issue**: "Firebase API key not configured"
- **Solution**: Set `FIREBASE_API_KEY` environment variable or create `~/.livecast/config.properties` with `firebase_api_key` property

**Issue**: "Authentication failed"
- **Solution**: Verify your API key is correct and authentication methods are enabled in Firebase Console

**Issue**: Network errors
- **Solution**: Check internet connection and ensure Firebase REST API is not blocked by firewall

### Web

**Issue**: "Firebase not initialized"
- **Solution**: Call `initializeFirebase()` before using auth methods, or set configuration in localStorage

**Issue**: "Firebase is not defined"
- **Solution**: Ensure Firebase JS SDK scripts are loaded in your HTML file

**Issue**: CORS errors
- **Solution**: Ensure your domain is authorized in Firebase Console → Authentication → Settings → Authorized domains

## API Reference

All platforms implement the same `AuthService` interface:

```kotlin
interface AuthService {
    val authState: StateFlow<AuthState>
    val currentUser: User?
    fun isLoggedIn(): Boolean
    suspend fun signInWithEmailAndPassword(email: String, password: String): AuthResult
    suspend fun createUserWithEmailAndPassword(email: String, password: String): AuthResult
    suspend fun signInAnonymously(): AuthResult
    suspend fun signOut()
    suspend fun sendPasswordResetEmail(email: String): AuthResult
}
```

### Usage Example

```kotlin
// Get auth service from Koin
val authService = get<AuthService>()

// Observe auth state
LaunchedEffect(Unit) {
    authService.authState.collect { state ->
        when (state) {
            is AuthState.Authenticated -> println("User: ${state.user.email}")
            is AuthState.Unauthenticated -> println("Not logged in")
            is AuthState.Loading -> println("Loading...")
            is AuthState.Error -> println("Error: ${state.message}")
        }
    }
}

// Sign in
val result = authService.signInWithEmailAndPassword("user@example.com", "password123")
when (result) {
    is AuthResult.Success -> println("Logged in: ${result.user.email}")
    is AuthResult.Error -> println("Error: ${result.message}")
}
```

## Security Notes

### Desktop
- ⚠️ API keys stored in config files are visible to users with file system access
- ✅ Use environment variables in production/server deployments
- ✅ ID tokens are stored locally for session persistence
- ⚠️ Do not commit config files to version control

### Web
- ⚠️ Firebase API keys in web apps are meant to be public (they identify your project)
- ✅ Security is enforced through Firebase Security Rules, not API key secrecy
- ✅ Use Firebase Security Rules to restrict access to your resources
- ✅ Enable App Check for additional security in production

## Next Steps

- Set up Firebase Security Rules for your database
- Enable additional authentication providers (Google, GitHub, etc.)
- Implement profile management
- Add email verification
- Set up Cloud Functions for advanced auth workflows
