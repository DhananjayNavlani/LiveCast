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

1. Get a service-account key once: Firebase console → Project settings → Service accounts → **Generate new private key**. Save it as `~/.livecast/server-sa.json` and run `chmod 600 ~/.livecast/server-sa.json`. Never put it in the repo. The key grants admin access to the project; delete it (Google Cloud console → IAM → Service accounts → Keys) once Cloud Run is up.
2. Start the server: `server/run-local.sh` (port 8090; 8080 belongs to the Firestore emulator).
3. Point the clients at it:
   - desktop: `livecast_server_url=http://localhost:8090` in `~/.livecast/config.properties` (or env `LIVECAST_SERVER_URL`);
   - Android debug: `livecast.serverUrl=http://localhost:8090` in `local.properties`, then run `adb reverse tcp:8090 tcp:8090` for each connected phone or emulator. Debug builds allow cleartext only to `localhost`, `127.0.0.1` and `10.0.2.2` (`androidClientApp/src/debug`); release builds need an HTTPS URL.
4. Smoke test: `curl -i localhost:8090/healthz` → `200`; `curl -i -X POST localhost:8090/v1/devices/x/checks` → `401`.

## Deploy to Cloud Run (later)

Runs as a dedicated service account through the metadata server (`GoogleCredentials.getApplicationDefault()`), so no key file exists.

1. Switch the Firebase project to the Blaze plan, then install the CLI and log in:
   ```bash
   brew install --cask google-cloud-sdk
   gcloud auth login
   gcloud config set project livecast-b73ad
   gcloud services enable run.googleapis.com artifactregistry.googleapis.com fcm.googleapis.com firestore.googleapis.com
   ```
2. Least-privilege service account (Firestore read + FCM send only):
   ```bash
   gcloud iam service-accounts create livecast-check --display-name "LiveCast check server"
   SA=livecast-check@livecast-b73ad.iam.gserviceaccount.com
   gcloud projects add-iam-policy-binding livecast-b73ad --member "serviceAccount:$SA" --role roles/datastore.viewer
   gcloud projects add-iam-policy-binding livecast-b73ad --member "serviceAccount:$SA" --role roles/firebasecloudmessaging.admin
   ```
3. Image registry, build and push (Apple silicon builds an amd64 image):
   ```bash
   gcloud artifacts repositories create livecast --repository-format docker --location asia-south1
   gcloud auth configure-docker asia-south1-docker.pkg.dev
   ./gradlew :server:buildFatJar
   IMAGE=asia-south1-docker.pkg.dev/livecast-b73ad/livecast/server:$(git rev-parse --short HEAD)
   docker build --platform linux/amd64 -f server/Dockerfile -t "$IMAGE" .
   docker push "$IMAGE"
   ```
4. Deploy. The endpoint does its own Firebase ID-token auth, so Cloud Run IAM auth stays off. One instance keeps the in-memory rate limiter consistent:
   ```bash
   gcloud run deploy livecast-check --image "$IMAGE" --region asia-south1 --service-account "$SA" \
     --allow-unauthenticated --set-env-vars FIREBASE_PROJECT_ID=livecast-b73ad \
     --min-instances 0 --max-instances 1 --cpu-boost --memory 512Mi
   ```
5. Use the printed `https://…run.app` URL as `livecast.serverUrl` (Android, `local.properties` or `-Plivecast.serverUrl=`) and `livecast_server_url` (desktop). Then delete the local dev key.

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
