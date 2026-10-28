#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
if ! command -v java >/dev/null 2>&1 || ! command -v javac >/dev/null 2>&1; then
  echo "A JDK 17 or newer is required (both java and javac must be on PATH)." >&2
  exit 1
fi
mkdir -p build/classes
javac --release 17 -encoding UTF-8 -d build/classes \
  src/main/java/com/flightbooking/Main.java \
  src/main/java/com/flightbooking/DataStore.java \
  src/main/java/com/flightbooking/Json.java
exec java --add-modules jdk.httpserver -cp build/classes com.flightbooking.Main
