# iOS + web clients — design

Date: 2026-11-14 · Branch `adddevice` · Builds on: `2026-11-13-pairing-design.md` (pairing, SOLID principles) and `~/.claude/plans/the-idea-of-livecast-crystalline-unicorn.md`.

## Intent
Parents should be able to supervise from an iPhone or a browser, not only from Android or desktop. The user pulled both clients forward from M4 to come right after pairing. Scope here: both clients reach **pairing parity** (sign in, My devices, add a device by code, remove a device, viewer placeholder). Screen cast on these clients comes with the cast slice.

## Decided by the user
- **Web = the existing React app `web/`** (Vite, React 18, TypeScript, Tailwind, Firebase JS SDK 11). Not CMP wasm.
- **iOS = `iosApp` running the shared `:client` framework** (same Compose UI and view models as Android/desktop).
- **iOS data path = option B:** Swift implements our small `DocumentStore` interface once over the Firebase iOS SDK; iOS reuses the shared DocumentStore-based pairing repositories. The interface is extended when a feature needs a new primitive.
- Order: after pairing is finished (refactor round 8, rules deploy, manual Android/desktop test): web first, then iOS.

## Goals / non-goals / anti-goals
- **Goals:** pairing parity on web and iOS against the same Firestore schema and rules; SOLID throughout (repository interfaces, DI-bound implementations); repair the broken `iosApp` build.
- **Non-goals:** screen cast/WebRTC on web or iOS; Google sign-in on iOS (email/password first); hosting or deploying the web app (Vercel) or the iOS app (TestFlight); iOS MDM (separate track).
- **Anti-goals:** any web path that bypasses the pairing rules (e.g. keeping the old "list every online broadcaster" Stage flow reachable); Swift code holding pairing logic that already lives in Kotlin.

## Current state (2026-11-14)
- `web/`: pages Landing/Login/Stage/Features, `AuthContext`, `WebRTCClient`/`SignalingClient` built on the old open `users`/`calls` model. No tests. npm installs need the Yarn mirror on this machine (Netskope blocks registry.npmjs.org; see `firestore-tests/.npmrc`, untracked).
- `iosApp`: its build phase runs `./gradlew :composeApp:embedAndSignAppleFrameworkForXcode` and searches `composeApp/build/...`. The module was renamed in R2, so the app has not built since. It imports the host framework `ComposeApp`. No Firebase SDK or Swift package is in the project; `GoogleService-Info.plist` is present. `:client` exports no iOS framework. Xcode 27.2.
- `DocumentStore` + `FirestoreDoc/FirestoreQuery/FirestoreWrite` live in `:firestore-grpc` (JVM only); the DocumentStore-based pairing repositories live in `:client` desktopMain.

## Design

### Web (React)
- **Contract:** `web/src/pairing/contract.ts` holds the collection names, field names and status values, mirroring Kotlin `FirestorePaths`/`PairingFields`. The rules tests (`firestore-tests/`) import the same constants, so web and rules cannot drift silently. A small check script compares the TS constants with the Kotlin ones *(Claude's call)*.
- **SOLID in TS:**
  - `PairingRequestRepository` and `SupervisedDeviceRepository` TS interfaces mirror the Kotlin ones, with `FirestorePairing*Repository` implementations on the Firebase JS SDK (`onSnapshot` for live updates).
  - Pure policy (`validateCode`, `requestFields`, `deviceIdFromSupervisorPath`) lives in one module.
  - Hooks (`useAddDevice`, `useMyDevices`) mirror the Kotlin view-model state machines, including the expiry timer, the busy guard, the guest message, error states and re-subscription on user change.
  - Implementations are provided through a React context (`PairingProvider`), so tests swap in fakes.
- **Routes:** login → `/devices` (My devices) → `/devices/add` → `/devices/:id` (viewer placeholder). The old Stage page and WebRTC services are removed from routing; the files stay until the cast slice.
- **Copy:** identical to the Kotlin clients ("Code invalid or expired", "Sign in with an account to add a device", "Request declined", "Code expired before the phone approved. Ask for a new code.").
- **Tests:** Vitest for the policy, both hooks (with fake repositories) and the contract check. Gates: `npm run lint`, `npm run build`, `npx vitest run`.

### iOS (`:client` + Swift bridges)
1. **Shared data seam:** move `DocumentStore` and its models to a new KMP module `:document-store` (commonMain). Timestamps become `DocTimestamp(epochMillis: Long)` instead of `java.time.Instant` *(Claude's call: avoids the experimental `kotlin.time.Instant`)*. `:firestore-grpc` implements it. The DocumentStore-based pairing repositories move from `:client` desktopMain to commonMain. Desktop behaviour is unchanged.
2. **Framework + app rewire:** `:client` exports an iOS framework `LiveCastClient` with `ClientMainViewController()`, which starts Koin and renders `ClientRoot`. `iosApp`'s build phase and search paths move to `:client:embedAndSignAppleFrameworkForXcode`, and `ContentView` imports `LiveCastClient`. This fixes the broken build.
3. **Firebase iOS SDK + sign-in bridge (Band C):**
   - `firebase-ios-sdk` is added through Swift Package Manager (FirebaseAuth, FirebaseFirestore).
   - Kotlin declares `IosAuthBridge` (email/password sign-in, sign-up, password reset, sign-out, ID token, an auth-state callback). Swift `FirebaseAuthBridge` implements it with FirebaseAuth.
   - `IosAuthService(bridge)` replaces the stub, and the app registers the bridge at launch, before Koin starts.
4. **Firestore bridge:**
   - Kotlin declares a callback-style `IosDocumentStoreBridge`: get, listen document, listen query (collection group + equality filters), create with auto id, batched commit, and a cancel handle. It uses plain types that cross to Swift cleanly.
   - Swift `FirebaseDocumentStoreBridge` implements it with FirebaseFirestore.
   - Kotlin `BridgedDocumentStore(bridge) : DocumentStore` adapts the callbacks to `Flow`.
   - The iOS `pairingPlatformModule` binds `DocumentStore` to it, and the shared repositories do the rest.
- **Gates:**
  - `:client` iOS compile + `linkDebugFrameworkIosSimulatorArm64`;
  - `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator CODE_SIGNING_ALLOWED=NO build`;
  - unit tests for `BridgedDocumentStore`, using a fake bridge (Kotlin, `iosSimulatorArm64Test` or commonTest with a fake);
  - all existing desktop/Android gates.
  - Manual: sign in and pair on the iOS simulator against the deployed rules.

### Risks
- **Swift Package Manager in an unattended run:** adding a package means editing `project.pbxproj`. agy attempts it, gated by `xcodebuild`. If that fails, the user adds `firebase-ios-sdk` once via Xcode (File → Add Package Dependencies).
- **Firebase iOS app registration:** `GoogleService-Info.plist`'s bundle id must match the iOS app's bundle id. This gets checked in step 3.
- **First-run SPM resolution through the corporate proxy:** GitHub access needs checking.

## Left to the builder
- Exact web layout (reuse `components/Button`, `Input`, Tailwind).
- Bridge method shapes beyond the semantics above.
- Hook internals.
- Swift file layout.
