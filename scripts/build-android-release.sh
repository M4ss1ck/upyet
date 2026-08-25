#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
ANDROID_HOME=${ANDROID_HOME:-"$HOME/Android/Sdk"}
SIGNING_DIR="$HOME/.config/upyet/android-signing"
KEYSTORE="$SIGNING_DIR/android-release.jks"
CREDENTIALS="$SIGNING_DIR/credentials.env"
ALIAS=my-alarm
VERSION=$(sed -n 's/^upyet\.version=//p' "$ROOT_DIR/gradle.properties")
APK="$ROOT_DIR/app/build/outputs/apk/release/upyet-$VERSION-release.apk"

# UpYet is pure Kotlin with no native libraries, so there is a single universal APK and none of the
# per-ABI splitting the Tauri-based sibling projects need. Signing itself is done by AGP through the
# release signingConfig in app/build.gradle.kts, which reads the credentials exported below.

fail() {
  printf 'Android release build failed: %s\n' "$1" >&2
  exit 1
}

[[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/keytool" ]] || fail "JAVA_HOME must point to a JDK containing keytool"
[[ -d "$ANDROID_HOME/build-tools" ]] || fail "Android SDK build tools not found under $ANDROID_HOME"
command -v openssl >/dev/null 2>&1 || fail "openssl is required to generate signing credentials"

BUILD_TOOLS_VERSION=$(printf '%s\n' "$ANDROID_HOME"/build-tools/* | sort -V | tail -n 1)
APKSIGNER="$BUILD_TOOLS_VERSION/apksigner"
AAPT="$BUILD_TOOLS_VERSION/aapt"

[[ -x "$APKSIGNER" ]] || fail "apksigner not found in $BUILD_TOOLS_VERSION"
[[ -x "$AAPT" ]] || fail "aapt not found in $BUILD_TOOLS_VERSION"

umask 077
mkdir -p "$SIGNING_DIR"
chmod 700 "$SIGNING_DIR"

if [[ -e "$KEYSTORE" || -e "$CREDENTIALS" ]]; then
  [[ -f "$KEYSTORE" && -f "$CREDENTIALS" ]] || fail "signing material is incomplete in $SIGNING_DIR"
else
  PASSWORD=$(openssl rand -hex 32)
  "$JAVA_HOME/bin/keytool" -genkeypair \
    -keystore "$KEYSTORE" \
    -storetype PKCS12 \
    -storepass "$PASSWORD" \
    -keypass "$PASSWORD" \
    -alias "$ALIAS" \
    -keyalg RSA \
    -keysize 4096 \
    -validity 10000 \
    -dname "CN=UpYet"
  printf 'UPYET_ANDROID_KEYSTORE=%q\n' "$KEYSTORE" > "$CREDENTIALS"
  printf 'UPYET_ANDROID_KEY_ALIAS=%q\n' "$ALIAS" >> "$CREDENTIALS"
  printf 'UPYET_ANDROID_KEYSTORE_PASSWORD=%q\n' "$PASSWORD" >> "$CREDENTIALS"
fi

chmod 600 "$KEYSTORE" "$CREDENTIALS"
# shellcheck disable=SC1090
source "$CREDENTIALS"

[[ "$UPYET_ANDROID_KEYSTORE" == "$KEYSTORE" ]] || fail "credentials reference an unexpected keystore"
[[ "$UPYET_ANDROID_KEY_ALIAS" == "$ALIAS" ]] || fail "credentials reference an unexpected key alias"
[[ -n "$UPYET_ANDROID_KEYSTORE_PASSWORD" ]] || fail "keystore password is empty"

export UPYET_ANDROID_KEYSTORE UPYET_ANDROID_KEY_ALIAS UPYET_ANDROID_KEYSTORE_PASSWORD

"$ROOT_DIR/gradlew" -p "$ROOT_DIR" clean assembleRelease

[[ -n "$VERSION" ]] || fail "upyet.version is missing from gradle.properties"
[[ -f "$APK" ]] || fail "release APK not found at $APK"

# apksigner is the authority on whether the APK is really signed; a build whose signingConfig silently
# went missing still produces a file, and installing that fails on the device instead of here.
"$APKSIGNER" verify --verbose "$APK" | grep -q "Verified using v2 scheme (APK Signature Scheme v2): true" \
  || fail "release APK is not signed with the v2 scheme"

PACKAGE=$("$AAPT" dump badging "$APK" | sed -n "s/^package: name='\([^']*\)'.*/\1/p")
[[ "$PACKAGE" == "dev.upyet" ]] || fail "release APK has unexpected package: ${PACKAGE:-none}"

printf 'Signed Android release APK:\n'
printf '  %s\n' "$APK"
printf 'Back up %s and %s before distributing this APK.\n' "$KEYSTORE" "$CREDENTIALS"
