# LiveCast - Kotlin Multiplatform Project

[![Android Release](https://github.com/dhananjaynavlani/LiveCast/actions/workflows/android-release.yml/badge.svg)](https://github.com/dhananjaynavlani/LiveCast/actions/workflows/android-release.yml)

Real-time screen sharing and remote control application built with Kotlin Multiplatform, targeting Android, iOS, Web, and Desktop.

## 📥 Download

Download the latest Android APK from [GitHub Releases](https://github.com/dhananjaynavlani/LiveCast/releases/latest).

| Build Type | Description |
|------------|-------------|
| Release APK | Signed & optimized for production use |
| Debug APK | For testing and development |

## ✨ Features

- 📱 **Real-time Screen Streaming** - Stream your Android device screen to any browser
- 👆 **Remote Touch Control** - Control the streaming device with intuitive touch gestures
- ⚡ **Ultra-Low Latency** - Powered by WebRTC with <50ms latency
- 🔒 **Secure Connection** - End-to-end encrypted P2P connection
- 📺 **HD Quality** - Up to 1080p video streaming with adaptive bitrate
- 🌐 **Cross-Platform** - Works on Chrome, Firefox, Safari, Edge

## 📁 Project Structure

* `/composeApp` - Shared code for Compose Multiplatform applications
  - `commonMain` - Code common for all targets
  - `androidMain` - Android-specific code
  - `iosMain` - iOS-specific code
  - `desktopMain` - Desktop-specific code
  - `wasmJsMain` - Web (WASM) specific code

* `/iosApp` - iOS application entry point and SwiftUI code

* `/web` - React/Vite web application for viewing and controlling streams
  - Landing page with feature showcase
  - Download page with GitHub releases integration
  - Stream viewing and control interface

## 🔐 Firebase Authentication

This project uses Firebase Authentication across all platforms:

| Platform | Implementation |
|----------|---------------|
| **Android** | Firebase Android SDK with Auth UI |
| **iOS** | Local authentication (Firebase iOS SDK integration pending) |
| **Desktop** | Firebase Auth REST API |
| **Web** | Firebase JS SDK |

See [Firebase Desktop/Web Setup Guide](docs/FIREBASE_DESKTOP_WEB_SETUP.md) for detailed configuration instructions.

## 🚀 Running the Project

### Mobile & Desktop (Kotlin Multiplatform)

```bash
# Android - Open project in Android Studio

# iOS - Open iosApp in Xcode

# Desktop
./gradlew :composeApp:run

# Web (Kotlin/WASM)
./gradlew :composeApp:wasmJsBrowserDevelopmentRun
```

### Web Application (React/Vite)

```bash
cd web
npm install
npm run dev
```

The web app will be available at `http://localhost:5173`

## 🔄 CI/CD

This project includes automated CI/CD for Android releases. See [CI/CD Documentation](README-CI-CD.md) for details.

### Creating a Release

```bash
# Option 1: Git tag (recommended)
git tag v1.0.0
git push origin v1.0.0

# Option 2: Manual trigger via GitHub Actions
```

## 🛠️ Configuration

### GitHub Releases Integration

Update the GitHub configuration in `web/src/config/github.ts`:

```typescript
export const GITHUB_CONFIG = {
  owner: 'your-username',
  repo: 'LiveCast',
};
```

## 📚 Learn More

- [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)
- [Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform/#compose-multiplatform)
- [Kotlin/Wasm](https://kotl.in/wasm/)
- [WebRTC](https://webrtc.org/)

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## 📄 License

This project is licensed under the MIT License.
