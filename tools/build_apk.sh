#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ANDROID_SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
BUILD_TOOLS_VERSION="${BUILD_TOOLS_VERSION:-35.0.0}"
OUTPUT_APK="${1:-$ROOT_DIR/app/build/outputs/apk/debug/VeilAura-Bale-Selector-0.3.0-beta.apk}"
SIGNING_KEY="${SIGNING_KEY:-$ROOT_DIR/signing/veilaura-beta.keystore}"
KEYSTORE_PASSWORD="${KEYSTORE_PASSWORD:-android}"
KEY_PASSWORD="${KEY_PASSWORD:-$KEYSTORE_PASSWORD}"
KEY_ALIAS="${KEY_ALIAS:-androiddebugkey}"

if [[ -z "$ANDROID_SDK" || -z "${JAVA_HOME:-}" ]]; then
    echo "JAVA_HOME and ANDROID_HOME or ANDROID_SDK_ROOT must be set." >&2
    exit 2
fi

ANDROID_JAR="$ANDROID_SDK/platforms/android-35/android.jar"
TOOLS="$ANDROID_SDK/build-tools/$BUILD_TOOLS_VERSION"
WORK_DIR="$ROOT_DIR/build/manual-apk"

for required in "$ANDROID_JAR" "$TOOLS/aapt" "$TOOLS/aapt2" "$TOOLS/d8" "$TOOLS/apksigner" "$TOOLS/zipalign" "$JAVA_HOME/bin/javac" "$JAVA_HOME/bin/keytool"; do
    if [[ ! -e "$required" ]]; then
        echo "Required build tool is missing: $required" >&2
        exit 3
    fi
done

rm -rf "$WORK_DIR"
mkdir -p "$WORK_DIR/gen" "$WORK_DIR/classes" "$WORK_DIR/dex" "$(dirname "$OUTPUT_APK")" "$(dirname "$SIGNING_KEY")"

"$TOOLS/aapt2" compile \
    --dir "$ROOT_DIR/app/src/main/res" \
    -o "$WORK_DIR/resources.zip"

"$TOOLS/aapt2" link \
    -o "$WORK_DIR/base.apk" \
    -I "$ANDROID_JAR" \
    --manifest "$ROOT_DIR/app/src/main/AndroidManifest.xml" \
    --java "$WORK_DIR/gen" \
    --min-sdk-version 26 \
    --target-sdk-version 35 \
    --version-code 8 \
    --version-name 0.3.0-beta \
    --auto-add-overlay \
    -R "$WORK_DIR/resources.zip"

find "$ROOT_DIR/app/src/main/java" "$WORK_DIR/gen" -name '*.java' -print0 | \
    xargs -0 "$JAVA_HOME/bin/javac" \
        --release 8 \
        -encoding UTF-8 \
        -cp "$ANDROID_JAR" \
        -d "$WORK_DIR/classes"

find "$WORK_DIR/classes" -name '*.class' -print0 | \
    xargs -0 "$TOOLS/d8" \
        --min-api 26 \
        --lib "$ANDROID_JAR" \
        --output "$WORK_DIR/dex"

cp "$WORK_DIR/base.apk" "$WORK_DIR/unaligned.apk"
(
    cd "$WORK_DIR/dex"
    "$TOOLS/aapt" add "$WORK_DIR/unaligned.apk" classes.dex
)
"$TOOLS/zipalign" -f -p 4 "$WORK_DIR/unaligned.apk" "$WORK_DIR/aligned.apk"

if [[ ! -f "$SIGNING_KEY" ]]; then
    "$JAVA_HOME/bin/keytool" -genkeypair \
        -keystore "$SIGNING_KEY" \
        -storepass "$KEYSTORE_PASSWORD" \
        -keypass "$KEY_PASSWORD" \
        -alias "$KEY_ALIAS" \
        -dname "CN=VeilAura Queue Beta,O=VeilAura,C=IR" \
        -keyalg RSA \
        -keysize 2048 \
        -validity 10000
fi

"$TOOLS/apksigner" sign \
    --ks "$SIGNING_KEY" \
    --ks-key-alias "$KEY_ALIAS" \
    --ks-pass "pass:$KEYSTORE_PASSWORD" \
    --key-pass "pass:$KEY_PASSWORD" \
    --out "$OUTPUT_APK" \
    "$WORK_DIR/aligned.apk"

"$TOOLS/apksigner" verify --verbose "$OUTPUT_APK"
"$TOOLS/zipalign" -c 4 "$OUTPUT_APK"
sha256sum "$OUTPUT_APK"
