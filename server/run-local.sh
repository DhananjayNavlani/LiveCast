#!/usr/bin/env bash
# Run the check server on this machine against the real Firebase project.
#
# Credentials: a service-account key file kept OUTSIDE the repo, default ~/.livecast/server-sa.json
# (Firebase console -> Project settings -> Service accounts -> Generate new private key; chmod 600).
# Overrides: GOOGLE_APPLICATION_CREDENTIALS, FIREBASE_PROJECT_ID, PORT.
#
# Clients: desktop uses livecast_server_url=http://localhost:8090 in ~/.livecast/config.properties;
# Android debug builds use livecast.serverUrl=http://localhost:8090 in local.properties plus
# `adb reverse tcp:8090 tcp:8090` for each connected phone or emulator.
set -euo pipefail
cd "$(dirname "$0")/.."

KEY="${GOOGLE_APPLICATION_CREDENTIALS:-$HOME/.livecast/server-sa.json}"
PROJECT="${FIREBASE_PROJECT_ID:-livecast-b73ad}"
PORT="${PORT:-8090}"   # 8080 belongs to the Firestore emulator (firebase.json)

if [[ ! -f "$KEY" ]]; then
  echo "No service-account key at $KEY" >&2
  echo "Download one: Firebase console > Project settings > Service accounts > Generate new private key," >&2
  echo "save it as $KEY and run: chmod 600 $KEY" >&2
  exit 1
fi
if [[ -n "$(find "$KEY" -perm +044 2>/dev/null)" ]]; then
  echo "WARNING: $KEY is readable by other users; run: chmod 600 $KEY" >&2
fi

./gradlew -q :server:buildFatJar
echo "LiveCast check server: project $PROJECT, http://localhost:$PORT (Ctrl-C to stop)"
exec env -u FIRESTORE_EMULATOR_HOST \
  FIREBASE_PROJECT_ID="$PROJECT" GOOGLE_APPLICATION_CREDENTIALS="$KEY" PORT="$PORT" \
  java -jar server/build/libs/server-all.jar
