# Pairing — M1 Band B, slice 1 (design)

Date: 2026-11-13 · Branch `adddevice` · Parent design: `~/.claude/plans/the-idea-of-livecast-crystalline-unicorn.md`

## Intent
LiveCast is a **child-monitoring product only** (decided 2026-11-13; the "own/managed devices" vertical is out). A parent uses a client app (Android `androidClientApp`, desktop `desktopApp`, both on shared `:client`) to pair with the child's phone (`androidHostApp`) by short code. Approval on the phone is the visible enrollment step (design invariant 1). This replaces today's model, where any signed-in user can read every user doc and call any online broadcaster, which violates the consent invariants. Pairing is the base for slice 2 (persistent supervision indicator) and slice 3 (screen cast), both of which need "who supervises which device".

## Goals
- Host shows a 6-digit code; parent enters it on a client; host shows the parent's identity with Allow / Deny; on Allow the client lists the device and the host shows "Supervised by …".
- Live updates on both sides, on the Android client and the desktop client.
- Several supervisors per device (e.g. both parents).
- A supervisor can remove themselves. **The host can never remove a supervisor** (decided by the user: child monitoring).
- Firestore security rules enforce all of the above for the new collections.

## Non-goals (this slice)
- The persistent supervision indicator (foreground-service notification / overlay) — slice 2. This slice shows an in-app "Supervised by" line only.
- Audit log of supervision actions (invariant 4) — later slice.
- Moving screen cast / signaling (`calls/`) onto the pairing model, and tightening the existing `users`/`calls`/`rooms` rules — slice 3.
- iOS client implementation (Band C; iOS gets a compiling stub) and web.
- Uninstall protection / Device Owner provisioning (M3). Known gap until then: the child can uninstall the app or revoke permissions and supervision silently stops; slice 2 (proposed) alerts the parent on "device offline / app removed" from the existing last-seen heartbeat.
- iOS client and iOS MDM: iOS MDM is a separate track (user, 2026-11-13); the iOS client `actual` follows Band C.
- Storing desktop tokens in the OS keychain.

## Anti-goals
- Any host-side "remove supervisor" path (UI or rules).
- Pairing that completes without the on-phone Allow tap; a code alone must never link devices.
- A *third-party* multiplatform Firebase library (the user rejected it: a common subset limits functionality). **Revised 2026-11-14 (user, option B):** our own small `DocumentStore` interface is allowed as the shared data seam for desktop (gRPC) and iOS (Swift bridge over the Firebase iOS SDK); we extend it when a feature needs a new primitive. Android keeps the full Firebase Android SDK.
- Hidden or misleading supervision: the Allow screen states that only a parent can remove supervision.

## Constraints
- Main checkout, branch `adddevice`; never push; `gradle.properties` user line never staged.
- No `dev.gitlive:*` or other multiplatform Firebase libraries. Android: official Firebase Android SDK (BoM 34.7.0, already in the catalog). Desktop: raw gRPC + Firestore v1 protos + REST, versions as in the spike (`io.grpc:* 1.83.0`, `com.google.api.grpc:{grpc,proto}-google-cloud-firestore-v1 3.52.0`, `org.json 20250107`), added to the version catalog.
- Gates that stay green: `:androidHostApp:assembleDebug :androidHostApp:compileKotlinDesktop :androidHostApp:compileKotlinIosSimulatorArm64 :androidClientApp:assembleDebug :desktopApp:compileKotlin :client:compileKotlinIosSimulatorArm64`.
- Deploying rules or indexes to the live project `livecast-b73ad` is outward-facing: the user confirms each deploy.

## Engineering principles (user requirement, 2026-11-14)
All code follows **SOLID** so features are plug-and-play and extensible:
- one responsibility per class;
- consumers depend on narrow interfaces shaped for them;
- concrete SDK and transport types (`FirebaseAuth`, `FirebaseFirestore`, `FirestoreGrpcClient`) appear only in platform implementation classes and Koin modules;
- each platform's implementations are bound in its own Koin module (`expect val …Module`), so a backend swaps without caller changes;
- pure logic (validation, field building, mapping, listen/reconnect state machines) exists once.

## Approaches considered
| Approach | Verdict |
|---|---|
| GitLive `firebase-kotlin-sdk` (shared Firestore API, JVM via `firebase-java-sdk`) | **Rejected by the user**: may limit functionality; JVM live-listener support undocumented. |
| Realtime Database for pairing/signaling; desktop via REST streaming (SSE) | Rejected: second database and rules file; Android/iOS would adopt RTDB only to make desktop easier. |
| **Firestore everywhere; desktop calls Firestore's gRPC `Listen` with the user's ID token** | **Chosen (user, option A).** Spike 2026-11-13 (`~/AndroidLearning/livecast-spike-listen`, commit `b0da1f1`): `LIVE_OK latencies_ms=[309,102,88]`; listen on a rule-less path returned REMOVE cause 7 "Missing or insufficient permissions", so rules apply to the end user. |

## Decided by the user
- Child monitoring only; host cannot remove a supervisor.
- `expect`/`actual` with native implementations per platform; no multiplatform Firebase library.
- Firestore + desktop gRPC (option A).
- Desktop pairs in this slice (follows from choosing live updates on desktop).

## Design

### Identity *(Claude's calls)*
- `deviceId` = the host's Firebase Auth uid (one device per host account). The host signs in as today (FirebaseUI).
- Supervisor = the client's Firebase uid. **Pairing requires a non-anonymous client account**: invariant 2 says the indicator names *who* supervises, and a guest has no identity (and loses supervision on reinstall). Guests see "Sign in with an account to add a device".
- Supervisor display name = `displayName ?: email`.

### Firestore data model
```
devices/{deviceId}                       hostUid, displayName, model, createdAt
devices/{deviceId}/supervisors/{uid}     uid, name, email, requestId, addedAt
pairingCodes/{code}                      deviceId, hostUid, createdAt, expiresAt   (code = 6 digits)
pairingRequests/{requestId}              code, deviceId, clientUid, clientName, clientEmail,
                                         status: pending|approved|denied, createdAt, decidedAt?
```
`firestore.indexes.json` gains a field override enabling the `supervisors.uid` single-field index at COLLECTION_GROUP scope (needed by the client's device-list query).

### Flow
1. **Host — "Pair a supervisor"** (entry on the host's stage screen): upsert `devices/{deviceId}` (`Build.MODEL`, device name); create `pairingCodes/{code}` with a `SecureRandom` 6-digit code via create-only semantics (on collision, retry up to 5 times); show the code with a 10-minute countdown; listen to `pairingRequests where deviceId == me and status == pending`, showing only requests created after the current code was issued.
2. **Client — "Add device"**: enter code → `get pairingCodes/{code}` (missing or expired → "Code invalid or expired") → create a `pairingRequests` doc → listen to that doc and show "Waiting for the phone to approve…".
3. **Host — request card**: "{name} ({email}) wants to supervise this device. Only a parent can remove supervision." [Allow] [Deny].
4. **Allow** = one batched write: request → `approved` + `decidedAt`; create `devices/{id}/supervisors/{clientUid}` (skip if it already exists; approve stays idempotent); delete `pairingCodes/{code}` (single use). **Deny** = request → `denied`; the code stays valid until expiry so the real parent can still use it.
5. **Client** sees `approved` → "My devices". The list = collection-group query `supervisors where uid == me` → each parent `devices/{id}` doc, live.
6. **Host** shows "Supervised by: A, B" from a live listener on `devices/{me}/supervisors`.
7. **Client — remove device** (with confirm): delete `devices/{id}/supervisors/{myUid}`. The host's line updates live.

### Security rules (new blocks; existing blocks unchanged)
- `pairingCodes/{code}`: `get` if signed in; `list` never; `create` if `auth.uid == hostUid == deviceId` and `expiresAt <= request.time + 11 min`; `delete` if `auth.uid == resource.data.hostUid`; `update` never.
- `devices/{deviceId}`: read if `auth.uid == deviceId` or `exists(devices/{deviceId}/supervisors/{auth.uid})`; create/update if `auth.uid == deviceId`; delete never.
- `pairingRequests/{id}`: `create` if `auth.uid == clientUid`, `status == 'pending'`, signed-in provider is not anonymous, and the referenced `pairingCodes/{code}` exists with matching `deviceId` and `expiresAt > request.time`; `read` (incl. the host's query) if `auth.uid` is the request's `clientUid` or `deviceId`; `update` only by `auth.uid == deviceId`, only `pending → approved|denied`, only `status`/`decidedAt` changed; delete never.
- `devices/{deviceId}/supervisors/{uid}`: `create` only by `auth.uid == deviceId` when `getAfter(pairingRequests/{requestId})` is `approved` with `clientUid == uid` and matching `deviceId`; read by the host or by `uid` itself; **delete only if `auth.uid == uid`**; update never.
- Collection group `/{path=**}/supervisors/{uid}`: read if `resource.data.uid == auth.uid`.

### Code structure
- **`:protocol`** (commonMain, no Firebase types): extend `HostDevice` and `PairingCode`; add `PairingRequest` (+ `PairingStatus`) and `Supervisor`.
- **`:auth`**: add `suspend fun getIdToken(forceRefresh: Boolean = false): String?` to `AuthService`. Android → `FirebaseUser.getIdToken`. Desktop → persist the `refreshToken` the REST sign-in already returns (currently discarded) next to `id_token`; refresh via `POST securetoken.googleapis.com/v1/token?key=…` when the token's `exp` is near or on force. iOS → `null` (Band C).
- **Host** (`androidHostApp` androidMain, `pairing/` package): `HostPairingRepository` (Firebase Android SDK: create code, pending-request listener, approve batch, deny, supervisors listener), `HostPairingViewModel`, `PairSupervisorScreen` (code + countdown + request cards), "Supervised by" line + "Pair a supervisor" entry on the stage screen, Koin module.
- **Client** (`:client`):
  - commonMain `pairing/`: `interface ClientPairingRepository { submitCode(code): Result<requestId>; observeRequest(id): Flow<PairingRequest>; observeMyDevices(): Flow<List<HostDevice>>; removeDevice(deviceId) }` + `expect fun createClientPairingRepository(auth: AuthService): ClientPairingRepository`, bound in Koin. `ClientPairingViewModel` (state machine: entering → submitting → waiting → approved | denied | invalid). Screens: `MyDevicesScreen` (list, Add, per-device remove), `AddDeviceScreen` (code entry → waiting → result). Login → My devices; tapping a device opens the existing `ViewerScreen` placeholder.
  - androidMain actual: official Firestore SDK (`:client` androidMain adds `firebase-firestore` via the BoM).
  - desktopMain actual: built on **`:firestore-grpc`**, a new Kotlin/JVM library ported from the spike's `Main.kt`: `FirestoreGrpcClient(projectId, tokenProvider)` with `listenDocument`, `listenQuery` (incl. collection group), `get`, `create`, `patch`, `delete`, `commit` (batched writes). Per-call auth metadata from `tokenProvider`; on UNAUTHENTICATED, reconnect with a forced refresh and resume the listen. Desktop-only helper, reused by slice 3 signaling; it is not a cross-platform API.
  - iosMain actual: stub returning a "not supported yet" error (compiles; Band C).
- **Desktop config**: project id read like the API key (env `FIREBASE_PROJECT_ID` or `firebase_project_id` in `~/.livecast/config.properties`).

### Edge cases
- Code missing/expired → client error; request denied → "Request declined".
- Host offline while a request is pending → it appears when the host is back, if its code is still current.
- Same parent pairs twice → approve is idempotent (existing supervisor doc kept).
- Desktop token expiry or network drop mid-listen → reconnect with backoff and fresh token.

## Verification
- Unit (commonTest): `:protocol` serialization; `ClientPairingViewModel` transitions with a fake repository.
- Rules: Firestore-emulator tests (`firebase` CLI and `node` are installed) in a `firestore-tests/` folder: client cannot list codes; cannot request with a wrong `deviceId` or expired code; anonymous client cannot request; host cannot delete a supervisor; supervisor can delete self; a stranger cannot read a device; supervisor creation without an approved request is denied. Run via `firebase emulators:exec --only firestore`.
- Build gates above, plus `:client` desktop/android compiles and the new module.
- Manual: host and `androidClientApp` on `emulator-5554` (different packages), plus `desktopApp`: pair → allow, deny, expired code, remove; confirm the host has no remove action and the rules reject a host-side delete.
- Rules + index deploy to `livecast-b73ad`: user-confirmed step before manual testing.

## Left to the builder
- Exact layout and styling (use `:designsystem`), copy beyond the Allow-screen sentence.
- Countdown/regeneration UX details, retry/backoff parameters.
- `FirestoreGrpcClient` internals (resume tokens, target bookkeeping).
- Package names within modules; rules-test harness layout.
