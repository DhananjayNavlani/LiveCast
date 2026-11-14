# LiveCast Check Service (`:server`)

Stateless Ktor JVM service that authenticates supervisors, verifies group membership, and relays on-demand checks over FCM HTTP v1.

## Environment Variables

- `FIREBASE_PROJECT_ID` (required): Firebase project ID (e.g. `demo-livecast` or `livecast-b73ad`).
- `GOOGLE_CREDENTIALS_JSON` (optional): Service account credentials JSON as an inline string. Takes precedence over `GOOGLE_APPLICATION_CREDENTIALS`.
- `GOOGLE_APPLICATION_CREDENTIALS` (optional): Path to service account credentials file.
- `PORT` (optional, default `8080`): Port to bind the HTTP server.
- `CORS_ALLOWED_ORIGINS` (optional): Comma-separated list of allowed origins for CORS preflight and headers (e.g. `https://app.livecast.com,http://localhost:3000`).
- `FIRESTORE_EMULATOR_HOST` (optional): Host and port of the Firestore emulator (e.g. `localhost:8080`). When set, Firestore directory calls bypass Google credentials and connect directly to the emulator with `Bearer owner`.

## Local Run Against Firestore Emulator

Start the emulator:
```bash
firebase emulators:start --only firestore --project demo-livecast
```

Run the server with emulator settings:
```bash
export FIREBASE_PROJECT_ID=demo-livecast
export FIRESTORE_EMULATOR_HOST=localhost:8080
export PORT=8080
./gradlew :server:run
```

## Local Run Against `livecast-b73ad` (dev)

1. Get a service-account key once: Firebase console → Project settings → Service accounts → **Generate new private key**. Save it as `~/.livecast/server-sa.json` and run `chmod 600 ~/.livecast/server-sa.json`. Never put it in the repo. The key grants admin access to the project; delete it (Google Cloud console → IAM → Service accounts → Keys) once a deployed server is up.
2. Start the server: `server/run-local.sh` (port 8090; 8080 belongs to the Firestore emulator).
3. Point the clients at it:
   - desktop: `livecast_server_url=http://localhost:8090` in `~/.livecast/config.properties` (or env `LIVECAST_SERVER_URL`);
   - Android debug: `livecast.serverUrl=http://localhost:8090` in `local.properties`, then run `adb reverse tcp:8090 tcp:8090` for each connected phone or emulator. Debug builds allow cleartext only to `localhost`, `127.0.0.1` and `10.0.2.2` (`androidClientApp/src/debug`); release builds need an HTTPS URL.
4. Smoke test: `curl -i localhost:8090/healthz` → `200`; `curl -i -X POST localhost:8090/v1/devices/x/checks` → `401`.

## Deploying

The server is a stateless container. It runs on any host that can run a Linux container or a JVM process and expose it over HTTPS: a serverless container platform, a PaaS, or a VM.

### 1. Build the artifact
- Fat jar: `./gradlew :server:buildFatJar` → `server/build/libs/server-all.jar`. It needs a Java 25 runtime: `java -jar server-all.jar`.
- Container image: build the jar first, then `docker build -f server/Dockerfile -t <registry>/<name>:<tag> .`. The Dockerfile only copies the prebuilt jar (the Gradle build needs the Android SDK, so it can't run inside the image), so a host that builds from git must run the Gradle step before `docker build`, or you push a prebuilt image to a registry the host can pull from.
- Most hosts run `linux/amd64`. On Apple silicon add `--platform linux/amd64` to `docker build`.

### 2. Configure the environment
| Variable | Value |
|---|---|
| `FIREBASE_PROJECT_ID` | `livecast-b73ad` |
| `PORT` | Usually injected by the host; defaults to `8080` |
| `GOOGLE_CREDENTIALS_JSON` or `GOOGLE_APPLICATION_CREDENTIALS` | Only when the host can't provide a Google identity (see step 3) |
| `CORS_ALLOWED_ORIGINS` | Only if a browser client calls the API |

Never set `FIRESTORE_EMULATOR_HOST` in a deployment.

### 3. Give it a Google identity with least privilege
The server reads Firestore (`devices/{id}`, `devices/{id}/private/push`, `groups/{g}/members/{email}`) and sends FCM messages. It needs exactly these permissions on the project:
- Firestore document read (`datastore.entities.get`, e.g. the *Cloud Datastore Viewer* role);
- FCM send (`cloudmessaging.messages.create`, e.g. the *Firebase Cloud Messaging Admin* role).

Create a dedicated service account with only those roles. Then, in order of preference:
1. **Workload identity / attached service account.** On Google-hosted runtimes, attach the service account to the service and set no credential variables. The server then falls back to Application Default Credentials and no key file exists.
2. **Secret environment variable.** On any other host, create a key for that service account and store its JSON in the host's secret manager as `GOOGLE_CREDENTIALS_JSON`. Never in the repo, the image, or plain config.
3. **Mounted secret file.** Mount the key file read-only and point `GOOGLE_APPLICATION_CREDENTIALS` at it.

Don't deploy with the Firebase Admin SDK key used for local development: it grants full project admin.

### 4. Expose it
- Serve HTTPS. The host's TLS termination or a reverse proxy in front is enough; the app speaks plain HTTP on `PORT`.
- Allow unauthenticated traffic at the platform level. Every `/v1` request is authenticated by the server itself with the caller's Firebase ID token.
- Health check: `GET /healthz` → `200`.

### 5. Scale and timeouts
- Run a single instance. The rate limiter is in memory, so several instances each allow a check every 10 s.
- The client gives the `POST` 10 s. If the host scales to zero, the first request after idle pays JVM start-up plus the first certificate and access-token fetch, which can exceed that and show "Check failed". Use the host's start-up CPU boost if it has one, or keep one instance warm.
- 512 MB of memory is enough.

### 6. Verify and wire the clients
1. `curl -i https://<host>/healthz` → `200`; `curl -i -X POST https://<host>/v1/devices/x/checks` → `401`.
2. Set the HTTPS base URL (no trailing path) in the clients:
   - Android: `livecast.serverUrl=https://<host>` in `local.properties`, or `-Plivecast.serverUrl=https://<host>` on the Gradle command line (release builds accept HTTPS only);
   - desktop: `livecast_server_url=https://<host>` in `~/.livecast/config.properties`, or env `LIVECAST_SERVER_URL`.
3. From a supervisor account, tap **Check** on an enrolled device: the row should end in "Online at HH:mm".
4. Delete the local development key once the deployed server works.

## Endpoints

- `GET /healthz`: Health check endpoint, returns 200 OK.
- `POST /v1/devices/{deviceId}/checks`: Relays a check request for `deviceId`. Requires `Authorization: Bearer <Firebase ID token>`.
  - `202 {"checkId": "<uuid>"}`: Check dispatched.
  - `401 {"status": "unauthorized"}`: Missing, invalid, or expired ID token.
  - `404 {"status": "device_not_found"}`: Device doc missing or unenrolled.
  - `403 {"status": "forbidden"}`: Caller is not an owner or supervisor in the device's group.
  - `429 {"status": "rate_limited"}`: Per-caller per-device check rate limit exceeded (10s interval).
  - `409 {"status": "no_push_token"}`: Device has not registered an FCM push token.
  - `410 {"status": "app_removed"}`: FCM returned unregistered (app removed / token invalid).
  - `502 {"status": "upstream_error"}`: Upstream Firestore or FCM failure.

## Building and Packaging

Two-step container build:
```bash
./gradlew :server:buildFatJar
docker build -f server/Dockerfile -t livecast-server:dev .
```
