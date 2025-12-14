# Firebase iOS Setup Instructions

This document explains how to complete the Firebase iOS setup for the LiveCast app.

## Prerequisites

1. A Firebase project (same one used for Android)
2. Xcode installed (version 13.0 or later)

## Setup Steps

### 1. Download GoogleService-Info.plist

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Select your project
3. Click on "Add app" or go to Project Settings > Your apps
4. Select iOS platform
5. Register the app with bundle ID: `com.dhananjay.livecast`
6. Download `GoogleService-Info.plist`
7. Copy it to `iosApp/iosApp/` folder

### 2. Add Firebase via Swift Package Manager

1. Open the Xcode project: `iosApp/iosApp.xcodeproj`
2. Go to **File > Add Package Dependencies...**
3. Enter the Firebase iOS SDK URL: `https://github.com/firebase/firebase-ios-sdk`
4. Select version **11.0.0** or later
5. Choose the following packages:
   - `FirebaseAuth`
   - `FirebaseCore`
6. Click **Add Package**

### 3. Enable Authentication Methods

In Firebase Console:
1. Go to Authentication > Sign-in method
2. Enable the following providers:
   - Email/Password
   - Anonymous (for guest sign-in)

### 4. Build the iOS App

You can now build and run the iOS app from Xcode.

## Architecture

The Firebase Auth integration uses:

1. **Swift Bridge** (`composeApp/src/nativeInterop/swiftklib/FirebaseAuthIOS/FirebaseAuthBridge.swift`)
   - Native Swift wrapper around Firebase Auth SDK
   - Exposes Objective-C compatible classes for Kotlin/Native interop

2. **Kotlin iOS Service** (`composeApp/src/iosMain/kotlin/com/dhananjay/livecast/auth/IosAuthService.kt`)
   - Implements the `AuthService` interface
   - Calls the Swift bridge using swiftklib generated bindings

3. **swiftklib** (Gradle plugin)
   - Compiles Swift code and generates Kotlin/Native interop bindings
   - Configuration in `composeApp/build.gradle.kts`

## Troubleshooting

### "No such module 'FirebaseCore'"
- Ensure Firebase SPM packages are added to the project
- Try **File > Packages > Reset Package Caches** in Xcode
- Clean build folder with **Product > Clean Build Folder** (Cmd+Shift+K)

### "GoogleService-Info.plist not found"
- Download from Firebase Console and place in `iosApp/iosApp/`
- Make sure it's added to the Xcode project (drag into the project navigator)

### Authentication methods not working
- Verify the methods are enabled in Firebase Console > Authentication > Sign-in method

## Features

The iOS auth implementation supports:
- ✅ Email/Password sign in
- ✅ Email/Password sign up
- ✅ Anonymous (guest) sign in
- ✅ Sign out
- ✅ Password reset email
- ✅ Auth state listening

