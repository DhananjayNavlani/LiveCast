# Host agent, on-demand check, app activity — design (slice 2)

Date: 2026-11-14 · Branch `adddevice` · Builds on `2026-11-14-closed-group-access-design.md` (groups, members, `devices/{uid}`, verified-email rules). That doc's SOLID principles and anti-goals still apply.

## Intent
A parent (group owner or supervisor) needs to know three things about a child's phone, whenever they ask:
- Is the phone reachable right now?
- Is LiveCast's accessibility service on? It's the agent and the remote-control hook. Children, or some phone brands, can turn it off.
- Which apps did the child install or remove?

The child's phone must stay visibly supervised, and turning the agent back on must be quick for the child. The app must not run a constant heartbeat on the child's phone.

## Decided by the user (2026-11-14)
- **Server:** a Ktor service that we write now. Where it's deployed is decided later. It must stay free-hostable: one Docker image, no local state.
- **Presence:** an on-demand **"Check" button** in the client replaces the periodic heartbeat.
- **Host agent:** the existing **accessibility service**. No foreground service.
- **Accessibility status:** the client is told whether the host's accessibility service is on or off. The host app has UI to turn it back on quickly.
- **App changes:** a broadcast receiver catches them, and the host reports them.
- **Reporting style A:** reports are shown when the parent looks (an activity list and an on/off badge), written to Firestore by the phone. No push notifications to parents in this slice.
- **Approach 1** (below): a stateless Ktor service on plain REST, with the check reply coming back through Firestore.
- Still in force: no Firebase Blaze or Cloud Functions; child monitoring only; the host can never remove its supervision.

## Goals / non-goals / anti-goals
- **Goals:**
  - A supervisor taps Check and gets **Online**, **Offline** or **App removed** within ~20 s.
  - The supervisor sees the host's accessibility state (on, off, or unknown) and when it was last reported.
  - The supervisor sees a device's recent app installs and removals.
  - On the host, re-enabling accessibility takes one tap from the persistent "Accessibility is off" notification or the launcher shortcut to the settings toggle, or two taps via the home-screen card. Sideloaded installs get a guided "Allow restricted settings" step first.
  - Supervision stays visible: slice 2 adds no new way to hide the icon or disable the agent, and notification permission is required at enrollment and reported to the parent. The existing closed experimental feature (the `config.showIcon` launcher toggle and the `ACTION_STOP_ACCESSILIBITY_SERVICE` → `disableSelf()` path) stays as it is (user decision, 2026-11-14).
  - The service builds into one Docker image, configured only by environment variables.
- **Non-goals (later):**
  - Deploying the service.
  - Push notifications to parents.
  - Commands other than check: ring, lock, locate, cast.
  - Check and activity UI in the web (React) and iOS clients. The data model supports them.
  - Uploading a full app inventory.
  - Play Console accessibility declaration work.
  - The legacy cast flow and its `users/{uid}` presence.
- **Anti-goals:**
  - Any periodic background polling or heartbeat on the host.
  - A service-account key, or any other server secret, inside an app.
  - A non-member or `device`-role user being able to trigger a check or read activity.
  - Covert monitoring: the supervision notification shows whenever the agent runs.
  - Checks used to spam-wake the child's phone.

## Constraints
- Firebase Spark plan only. Firestore free quotas (50k reads / 20k writes per day). FCM is free.
- The service uses a service account from `livecast-b73ad`, passed in by environment variable and never committed.
- Android `minSdk 26`, `targetSdk 36`, Kotlin 2.3.21, Koin. SOLID is a standing requirement: narrow interfaces, no SDK types outside platform implementations and DI modules, pure logic lives once.
- The desktop client reads Firestore through `:firestore-grpc` `DocumentStore`. `FirestoreQuery` has no `orderBy` or `limit` today.

## Approaches (user picked 1)
1. **Stateless Ktor on REST, check reply via Firestore (picked).**
   - Ktor server plus Ktor client. Google's auth library mints service-account access tokens. A JWT library (nimbus-jose-jwt) verifies Firebase ID tokens. Firestore REST for reads, FCM HTTP v1 for sends.
   - The host replies by writing its device doc, which clients already listen to.
   - ~100 MB of memory, runs on any host. Costs more of our own code than option 2.
2. **The same design on the Firebase Admin SDK.** Less code, but it pulls in gRPC/Netty: ~250 MB+ and a slower cold start, which is tight on free tiers.
3. **Stateful relay.** The host answers the service over HTTP while the service holds the client's call open. One call instead of a Firestore write, but it can only run as a single instance, the call blocks for up to 20 s, and it's harder to test.

## Design

### Data model (Firestore)
`devices/{uid}`:
- **Removed:** `online` and `lastSeen`. Existing docs may still carry them; readers ignore them.
- **Added:**
  - `accessibility`: `"on"` | `"off"`.
  - `notifications`: `"on"` | `"off"` (whether the host may post notifications, i.e. whether the supervision notice can show).
  - `statusAt`: timestamp of the last `accessibility`/`notifications` report.
  - `lastCheckId`: string.
  - `lastCheckAt`: server timestamp of the host's last check reply.
- **Enrollment:** a create now requires `groupId, hostUid, displayName, model, enrolledAt`. It may also include `accessibility`, `notifications` and `statusAt`.

`devices/{uid}/private/push`:
- `{ fcmToken, updatedAt }`.
- Only the host (`request.auth.uid == uid`) can read or write it. The service reads it with the service account, which bypasses rules.
- Owners and supervisors never see the token.

`devices/{uid}/appEvents/{autoId}`:
- `{ type: "installed" | "removed", packageName, label, at, detectedLate, groupId }`.
- `at` is the **device-reported** time the change happened, not the server write time, because writes may sit in the offline queue:
  - receiver events use the time the broadcast arrived;
  - installs found by the startup diff use `PackageInfo.firstInstallTime`;
  - removals found by the diff use the detection time, with `detectedLate = true`.
- `groupId` is the device's group at write time. A device re-enrolled into another group never exposes old history to the new group.
- Only the host can create, and only while the device doc has a non-null `groupId` equal to the event's `groupId`. Exact keys are validated, and `at` must be a timestamp no later than `request.time + 5 min`.
- Owners and supervisors can read, only when `resource.data.groupId` equals the device's current `groupId` and their role in that group is owner or supervisor.
- Nobody can update or delete.
- No retention or cleanup in this slice.

### Shared protocol (`:protocol`)
- **`HostDevice` drops** `online` and `lastSeenEpochMs`.
- **`HostDevice` gains:**
  - `accessibility: ToggleState` and `notifications: ToggleState` (`ON`, `OFF`, `UNKNOWN` when the field is absent);
  - `statusAtEpochMs: Long?`;
  - `lastCheckId: String?`;
  - `lastCheckAtEpochMs: Long?`.
- **New:** `AppEvent(type: AppEventType, packageName, label, atEpochMs, detectedLate: Boolean, groupId)`, plus field constants and builders for each new write (in the style of `GroupWrites`).
- **Removed:**
  - `GroupRules.isOnline`, `ONLINE_WINDOW_MS`, `HEARTBEAT_INTERVAL_MS`, `PRESENCE_TICK_MS`;
  - `GroupWrites.heartbeatFields`.
- **New check constants:**
  - `CheckRules.REPLY_TIMEOUT_MS = 20_000`;
  - `CheckRules.FCM_TTL_SECONDS = 30`;
  - `CheckRules.MIN_INTERVAL_MS = 10_000` (rate limit per caller per device).
- `FirestorePaths` gains `privatePush(uid)` and `appEvents(uid)`.

### Rules (`firestore.rules` + emulator tests)
- **`isDeviceHeartbeat` becomes `isDeviceSelfReport`:**
  - the caller is the device itself;
  - `affectedKeys` contains only `displayName, model, accessibility, notifications, statusAt, lastCheckId, lastCheckAt`;
  - `accessibility` and `notifications` are each in `['on','off']` whenever present.
- **Create and re-enroll** key lists are updated to the data model above.
- **New:** `match /devices/{d}/private/{doc}` (the device's own uid only).
- **New:** `match /devices/{d}/appEvents/{e}`:
  - create by the device, while enrolled, with exactly the listed keys, a `type` from the allowed values, `groupId` equal to the device's current `groupId`, and `at` a timestamp no later than `request.time + 5 min`;
  - read by owners and supervisors of `get(devices/d).groupId`, only when the event's `groupId` matches it;
  - no update or delete.
- **Existing tests:** M1 tests that wrote `online`/`lastSeen` are rewritten to the new fields. Tests are added for every rule above: allowed and denied cases, including a supervisor reading the push token (denied), a removed device writing an event (denied), and the new group's supervisor reading an event from the device's previous group (denied).

### Service (`:server`, new JVM module)
**Endpoint:** `POST /v1/devices/{deviceId}/checks`, with `Authorization: Bearer <Firebase ID token>`.

1. Verify the ID token:
   - RS256 signature, using Google's `securetoken@system.gserviceaccount.com` certificates, cached per their `Cache-Control`;
   - `aud` = project id, `iss` = `https://securetoken.google.com/<project>`;
   - `exp`/`iat` valid, `sub` non-empty, `email_verified == true`.
   - Any failure → **401**.
2. Read `devices/{deviceId}`. Missing, or `groupId == null` → **404**.
3. Read `groups/{groupId}/members/{lowercased email}`. The role must be `owner` or `supervisor`; otherwise → **403**.
4. Rate limit: in memory, best effort, `MIN_INTERVAL_MS` per (caller uid, device). Exceeded → **429**.
5. Read `devices/{deviceId}/private/push`. No token → **409** `{"status":"no_push_token"}`.
6. Send an FCM HTTP v1 message:
   - data `{type:"check", checkId}`;
   - `android.priority = HIGH`, `ttl = FCM_TTL_SECONDS`;
   - `checkId` is a fresh random UUID.
7. Map the FCM result:
   - Sent → **202** `{"checkId": "..."}`;
   - `UNREGISTERED`, or `NOT_FOUND` for that token → **410** `{"status":"app_removed"}`;
   - anything else → **502**.

**Also:**
- `GET /healthz` → 200.
- Configurable CORS allow-list for a future web client.

**Seams, each a narrow interface bound in one Koin (or plain constructor) module:**
- `IdTokenVerifier`
- `DeviceDirectory` (`groupOf(deviceId)`, `pushTokenOf(deviceId)`)
- `MembershipDirectory` (`roleOf(groupId, email)`)
- `PushSender` (`sendCheck(token, checkId): PushResult`)
- `CheckRateLimiter`
- `IdGenerator`
- `Clock`

`CheckService` is the pure orchestration over these and holds no transport types.

**Implementations:**
- `GoogleIdTokenVerifier` (nimbus-jose-jwt)
- `FirestoreRestDirectory` (Ktor client + Google auth library)
- `FcmHttpV1PushSender`

**Config (environment only):**
- `FIREBASE_PROJECT_ID`
- `GOOGLE_CREDENTIALS_JSON` (inline JSON) or `GOOGLE_APPLICATION_CREDENTIALS` (path)
- `PORT` (default 8080)
- `CORS_ALLOWED_ORIGINS`
- `FIRESTORE_EMULATOR_HOST` (tests and local runs)

**Build and packaging:**
- Fat jar plus a multi-stage `server/Dockerfile`.
- `:server` depends on `:protocol` (its JVM variant) for paths and field names.

### Host app (`androidHostApp`)
**FCM:**
- Add `firebase-messaging`.
- `LiveCastMessagingService`:
  - `onNewToken` → `PushTokenRegistry.register(token)`.
  - `onMessageReceived(type == "check")` → `CheckResponder.respond(checkId)`. It writes `lastCheckId`, `lastCheckAt`, `accessibility`, `notifications` and `statusAt` in one update, bounded to 10 s.
  - If accessibility is off at that moment, it also posts the **"Accessibility is off — tap to turn on"** notification. Tapping it opens the re-enable screen.
- The token is also registered after enrollment completes and on app start, if enrolled.
- Nothing is written when signed out or not enrolled.

**Agent: the existing `cast.data.services.AccessibilityService`:**
- **Legacy cleanup in `LiveCastService.startObservers()`:**
  - **Stop calling `remoteDataSource.setUserOnline(...)`.** That's the 30 s `users/{uid}` heartbeat loop. Only the legacy "online users" list on other hosts' Stage screens reads a host's `is_online`; the web app writes its own `is_online` and never reads a host's, so web viewers still appear there.
  - **Keep** the `config.showIcon` launcher toggle and the `ACTION_STOP_ACCESSILIBITY_SERVICE` → `disableSelf()` path untouched. They are a closed experimental feature (user decision, 2026-11-14). When `disableSelf()` runs, the normal stop path reports `accessibility: off` and shows the off notice.
  - The gesture and key-event observers for remote input stay.
- `onServiceConnected` keeps the remaining observers and also calls `HostAgent.start()`.
- `onUnbind` and `onDestroy` call `HostAgent.stop()`.
- `HostAgent` runs a Koin-provided list of `AgentComponent { start(); stop() }`. Components are no-ops unless the device is enrolled.
  - **`AccessibilityStateReporter`:** writes `accessibility: on` plus the current `notifications` state on start, and `accessibility: off` on stop (best effort).
  - **`AppChangeMonitor`:**
    - On start, it diffs installed user apps against an on-device baseline:
      - `getInstalledPackages`, excluding `FLAG_SYSTEM` apps and our own package;
      - the baseline maps packageName → label, kept in local storage (DataStore or SharedPreferences, builder's choice).
    - It writes an `installed` or `removed` event for each difference, then saves the new baseline.
    - First run (no baseline): save the baseline and write no events. The baseline is cleared when enrollment completes and when the device is removed from its group, so each enrollment starts fresh.
    - It registers a runtime receiver for `PACKAGE_ADDED` and `PACKAGE_REMOVED` (data scheme `package`). Broadcasts with `EXTRA_REPLACING` are ignored, since those are updates. Each event also updates the baseline.
    - Removed events take their label from the baseline.
    - Writes use Firestore's offline queue.
  - **`SupervisionNotifier`:**
    - On start, it posts the ongoing **"Supervised by {owner name or email}"** notification. Its `deleteIntent` re-posts it if dismissed (Android 14+ lets users swipe it away).
    - On stop, it **replaces** that notification with a persistent **"Accessibility is off — tap to turn on"** one. Tapping it goes straight to the settings intent below.
- **`AgentOffNotice` receiver** for `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED`: if the device is enrolled and accessibility is off, it posts the same "Accessibility is off" notification. It does no other work.
- **Static launcher shortcut** "Turn on accessibility", reached by long-pressing the icon. It opens the re-enable flow.
- **Notification permission (Android 13+):** the Enroll screen requests `POST_NOTIFICATIONS`, and "Continue" stays disabled until it's granted. If the child revokes it later, the agent keeps running and reports `notifications: off`, so the parent sees it.
- The pure parts (`AppChangeDiff`, the label lookup interface, the check reply builder) live outside Android classes so they can be unit-tested.
- **Manifest:** `QUERY_ALL_PACKAGES`, `RECEIVE_BOOT_COMPLETED`, the messaging service, the `AgentOffNotice` receiver, the shortcut XML, and the notification channels.

**Re-enable UI (Stage screen):** an `AccessibilityStatusCard` that refreshes on resume. It reads `AccessibilityManager` / `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES`, behind an `AccessibilityStatusSource` interface.
- **On:** "Accessibility service is on".
- **Off:** a **"Turn on"** button opens `ACTION_ACCESSIBILITY_SETTINGS` with the extras `:settings:fragment_args_key` and `:settings:show_fragment_args` set to our flattened component, so our row is highlighted. That's best effort; elsewhere it falls back to the plain list.
- **Android 13+ and not installed from a store** (via `InstallSourceInfo.packageSource`): show "Step 1: Allow restricted settings" first. It opens `ACTION_APPLICATION_DETAILS_SETTINGS` and explains "⋮ → Allow restricted settings". A "Skip" option is offered.

**Removed:** `HostPresenceViewModel`, `PresenceHeartbeat`, `PresenceReporter`, `FirestorePresenceReporter`, and their DI and tests. Enrollment and "Supervised by" stay. The legacy `users/{uid}` heartbeat in `RemoteDataSource`/`DeviceOnlineWorker` is **left alone**: it belongs to the old cast flow (*Claude's call*).

### Client (`:client`, Android + desktop)
**Check:**
- `DeviceCheckService` (commonMain interface): `suspend fun requestCheck(deviceId): CheckRequestResult`, one of `Requested(checkId)`, `AppRemoved`, `NoPushToken`, `NotAllowed`, `RateLimited`, `Failed(message)`.
- `KtorDeviceCheckService` sits in commonMain, with a per-platform engine expect/actual (OkHttp on Android, CIO or Java on desktop). It uses `IdTokenProvider`.
- Base URL comes from a `ServerConfig` interface:
  - Android: `BuildConfig.LIVECAST_SERVER_URL`, from the Gradle property or `local.properties` `livecast.serverUrl`;
  - desktop: `livecast_server_url` in `~/.livecast/config.properties` or the `LIVECAST_SERVER_URL` env var.
  - Missing config → `Failed("Server not configured")`.

**`MyDevicesViewModel.check(deviceId)`:**
- Per-row `CheckState`: `Idle`, `Checking`, `Online(atEpochMs)`, `Offline`, `AppRemoved`, `Error(message)`.
- On `Requested(checkId)`, it waits on the device stream it already observes until `device.lastCheckId == checkId` → `Online(lastCheckAt)`, or `REPLY_TIMEOUT_MS` passes → `Offline`.
- A second Check on the same row while one is `Checking` is ignored.
- State is cleared on re-bind (user switch).
- The waiting logic is pure commonMain and tested with virtual time.

**Row UI:** name, model and group, then:
- **"Accessibility: On / Off / Unknown · {HH:mm}"**;
- when `notifications == OFF`, a warning line **"Notifications blocked on the device"**;
- a **"Check"** button;
- the last result, e.g. **"Online at 14:05"**, "Offline (no reply in 20 s)", "App removed", "Not set up for checks" (409) or "Too many checks, wait a moment" (429).
- Absolute times are used, so no ticker is needed.

**Removed:** `PresenceClock`, `SystemPresenceClock`, the ticker, and `GroupRules.isOnline` usage.

**Device activity:**
- Tapping a row opens a `DeviceDetailScreen` with an **"App activity"** list: the last 50 events of the device's current group, newest first, e.g. "Installed WhatsApp · 14:05" or "Removed Instagram · 13:50". When `detectedLate` is set, the line reads "Removed Instagram · noticed 13:50". The screen also holds the existing viewer placeholder entry.
- Data comes from `AppEventRepository.observeRecent(deviceId, limit = 50)`, with Android SDK and `DocumentStore` implementations.
- `FirestoreQuery` gains `orderBy: List<Pair<String, Direction>>` and `limit: Int?` (gRPC `StructuredQuery`), with tests.
- Access lost (permission denied) → "You no longer have access".

### Failure handling
- **Client:** every Check outcome maps to visible row text. Repository streams keep the M1 error handling: catch, map to `GroupError`, and never crash a view model.
- **Host:** FCM handler writes are bounded and failures are logged. A failed reply simply makes the client time out to Offline. Agent components catch their own errors and never take down the accessibility service.
- **Service:** never logs ID tokens or FCM tokens. Every upstream failure maps to a status code, never a 500 with a stack trace.

### Testing
- **`:server`:**
  - `CheckService` with fakes, covering every status branch: 401, 404, 403, 429, 409, 202, 410, 502.
  - `GoogleIdTokenVerifier`, using a locally generated RSA key and certificate fixture (bad signature, wrong aud/iss, expired, unverified email).
  - `FcmHttpV1PushSender` with Ktor `MockEngine`, mapping `UNREGISTERED` → `AppRemoved`.
  - `FirestoreRestDirectory` against the Firestore emulator.
  - Route tests with `testApplication`.
- **Rules:** emulator tests, as listed above.
- **Host:** `AppChangeDiff` (first run, install with `firstInstallTime`, late removal flagged, label from baseline, system and own apps excluded, baseline reset on enrollment); `CheckResponder` and `AccessibilityStateReporter` with fakes, including the notifications state; the notifier's on/off swap; the re-enable intent builder; Enroll "Continue" gated on notification permission.
- **Client:** `MyDevicesViewModel` check flow (reply in time, timeout, app removed, 409, 429, double tap, user switch); `KtorDeviceCheckService` with `MockEngine`; `AppEventRepository` for both platforms; `FirestoreQuery` orderBy/limit.

## Claude's calls (flagged for review)
1. Removed app events take their label from the on-device baseline. On the first run the phone writes no events, only a baseline.
2. App updates are not reported. System apps are excluded.
3. Every time the agent starts, it diffs the full installed-app list against the baseline, rather than using `getChangedPackages` (whose sequence numbers reset on reboot).
4. The agent stops running the legacy `users/{uid}` 30 s heartbeat. Only other hosts' legacy online list read it, and web viewers still show there. The experimental `showIcon` toggle and self-disable path are kept (user decision).
5. Notification permission is required at enrollment. A later revoke is reported to the parent and does not stop monitoring.
6. When the agent stops, its "Supervised by" notification turns into an "Accessibility is off" notification. A boot/update receiver and a launcher shortcut make re-enabling one tap.
7. App events carry `groupId` and a device-reported time. The baseline resets on each enrollment.
8. Times are shown as absolute (`HH:mm`), not "2 min ago", so the client needs no ticker.
9. The rate limit is in memory and best effort. That's fine for a single instance.
10. Check replies go on the device doc, not a `checks` subcollection, so the existing listener carries them.
11. No retention or cleanup for `appEvents` in this slice.

## Left to the builder
- Library versions (Ktor 3.x, nimbus-jose-jwt, Google auth library) and the Docker base image.
- Notification channel ids and icons.
- Exact layouts within the copy above.
- DataStore vs SharedPreferences for the baseline.
- How the desktop config source is shared between `ServerConfig` and the existing Firestore config.
