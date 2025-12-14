This is a Kotlin Multiplatform project targeting Android, iOS, Web, Desktop.

* `/composeApp` is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
  - `commonMain` is for code that’s common for all targets.
  - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
    For example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
    `iosMain` would be the right folder for such calls.

* `/iosApp` contains iOS applications. Even if you’re sharing your UI with Compose Multiplatform, 
  you need this entry point for your iOS app. This is also where you should add SwiftUI code for your project.


Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html),
[Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform/#compose-multiplatform),
[Kotlin/Wasm](https://kotl.in/wasm/)…

We would appreciate your feedback on Compose/Web and Kotlin/Wasm in the public Slack channel [#compose-web](https://slack-chats.kotlinlang.org/c/compose-web).
If you face any issues, please report them on [GitHub](https://github.com/JetBrains/compose-multiplatform/issues).

You can open the web application by running the `:composeApp:wasmJsBrowserDevelopmentRun` Gradle task.

## Deployment

### Vercel Deployment

This project is configured to deploy the WasmJS build to Vercel. The configuration includes:

- **Build Command**: Gradle task to build the WasmJS distribution
- **Output Directory**: `composeApp/build/dist/wasmJs/productionExecutable`
- **Caching**: Gradle dependencies and build cache are configured for faster builds

#### Deployment Optimizations:

1. **Gradle Caching**: The build uses Gradle's configuration and dependency caching
2. **No Daemon Mode**: Builds run without Gradle daemon to reduce memory usage on Vercel
3. **Parallel Execution**: Gradle tasks run in parallel where possible
4. **Required Headers**: Cross-Origin-Opener-Policy and Cross-Origin-Embedder-Policy headers are set for WasmJS

To deploy:
```bash
vercel --prod
```

Note: First build may take longer as Gradle downloads dependencies. Subsequent builds will be faster due to caching.
