#!/usr/bin/env bash
# Builds Evolve's APK: the Rust engine with cargo-ndk, its Kotlin bindings
# with UniFFI, then the Compose app with Gradle.
#
#   android/build-apk.sh [debug|release]
#
# Needs ANDROID_HOME (SDK with platform 35 and build-tools 35), ANDROID_NDK_HOME,
# the Rust target aarch64-linux-android, cargo-ndk, and Gradle 8.
# The APK lands in android/app/build/outputs/apk/<type>/, named
# Evolve-<version>.apk (Evolve-<version>-debug.apk for debug builds).
set -euo pipefail

TYPE="${1:-release}"
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(dirname "$HERE")"
ABI=arm64-v8a
: "${ANDROID_HOME:?set ANDROID_HOME to the Android SDK}"
: "${ANDROID_NDK_HOME:?set ANDROID_NDK_HOME to the Android NDK}"
export ANDROID_NDK_ROOT="$ANDROID_NDK_HOME"

PROFILE=(--release)
[ "$TYPE" = debug ] && PROFILE=()
OUT="$HERE/build/jni"
GEN="$HERE/app/build/generated/uniffi"
rm -rf "$OUT" "$GEN"
(
  cd "$ROOT"
  # Unstripped: UniFFI reads the bindings' description from the library's
  # symbols. The copy in the APK is stripped below.
  CARGO_PROFILE_RELEASE_STRIP=false cargo ndk -t "$ABI" -P 26 -o "$OUT" \
    build "${PROFILE[@]}" --locked -p evolve
  # The Kotlin bindings (io.github.singularity098.evolve.core).
  cargo run --locked -q -p uniffi-bindgen -- generate \
    --library "$OUT/$ABI/libevolve.so" \
    --language kotlin --out-dir "$GEN" --no-format
)
ls -l "$GEN"/io/github/singularity098/evolve/core/
LIBS="$HERE/app/src/main/jniLibs/$ABI"
rm -rf "$LIBS"
mkdir -p "$LIBS"
cp "$OUT/$ABI/libevolve.so" "$LIBS/"
LLVM="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64"
"$LLVM/bin/llvm-strip" --strip-all "$LIBS/libevolve.so"
ls -l "$LIBS"

cd "$HERE"
TASK="assemble$(tr '[:lower:]' '[:upper:]' <<< "${TYPE:0:1}")${TYPE:1}"
# Once more if the first try fails: Maven repositories have bad moments.
"${GRADLE:-gradle}" --no-daemon "$TASK" || "${GRADLE:-gradle}" --no-daemon "$TASK"
ls -l "app/build/outputs/apk/$TYPE"
