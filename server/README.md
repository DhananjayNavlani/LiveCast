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

Build fat JAR:
```bash
./gradlew :server:buildFatJar
```

Build Docker container:
```bash
docker build -f server/Dockerfile -t livecast-server .
```
