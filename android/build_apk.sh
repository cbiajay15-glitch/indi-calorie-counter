#!/usr/bin/env bash
# Build a signed Indi Calorie Counter APK without Gradle, using the Android SDK tools directly.
# Needs: JDK 17+, Android SDK with platforms;android-35 and build-tools;35.0.0.
# Env:   ANDROID_HOME (SDK path), KEYSTORE (path to .jks), KS_PASS (keystore password),
#        VERSION_CODE (integer, increase for every release), VERSION_NAME (e.g. 1.0).
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
SDK="${ANDROID_HOME:?set ANDROID_HOME}"
BT="$SDK/build-tools/35.0.0"
JAR="$SDK/platforms/android-35/android.jar"
: "${KEYSTORE:?set KEYSTORE}" "${KS_PASS:?set KS_PASS}"
VERSION_CODE="${VERSION_CODE:-1}"
VERSION_NAME="${VERSION_NAME:-1.0}"
OUT="$HERE/build"

rm -rf "$OUT"
mkdir -p "$OUT/compiled" "$OUT/gen" "$OUT/classes" "$OUT/assets/www"

# 1. The web app goes in as assets (service worker and web manifest are not needed inside the app).
cp "$HERE/../index.html" "$HERE/../foods.js" "$HERE/../icon.svg" "$OUT/assets/www/"
cp -r "$HERE/../fonts" "$OUT/assets/www/fonts"

# 2. Resources and manifest.
"$BT/aapt2" compile --dir "$HERE/res" -o "$OUT/compiled/res.zip"
"$BT/aapt2" link -o "$OUT/base.apk" -I "$JAR" --manifest "$HERE/AndroidManifest.xml" \
  -A "$OUT/assets" --java "$OUT/gen" "$OUT/compiled/res.zip" \
  --min-sdk-version 24 --target-sdk-version 35 \
  --version-code "$VERSION_CODE" --version-name "$VERSION_NAME"

# 3. Java -> class files -> dex.
javac --release 11 -nowarn -cp "$JAR" -d "$OUT/classes" $(find "$HERE/src" "$OUT/gen" -name '*.java')
"$BT/d8" --release --min-api 24 --lib "$JAR" --output "$OUT" $(find "$OUT/classes" -name '*.class')

# 4. Package, align, sign, verify.
cp "$OUT/base.apk" "$OUT/unsigned.apk"
(cd "$OUT" && zip -q unsigned.apk classes.dex)
"$BT/zipalign" -p -f 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
"$BT/apksigner" sign --ks "$KEYSTORE" --ks-pass env:KS_PASS --out "$OUT/IndiCalorieCounter.apk" "$OUT/aligned.apk"
"$BT/apksigner" verify --print-certs "$OUT/IndiCalorieCounter.apk" | head -3
echo "APK: $OUT/IndiCalorieCounter.apk"
