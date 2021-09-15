#!/bin/sh
set -e

VERSION="$(grep 'def version = ' build.gradle | awk '{print $4}' | tr -d "'")"
DEST_DIR="$1"
[ -z "$DEST_DIR" ] && DEST_DIR="dist"

mkdir -p "$DEST_DIR"

bundletool_universal() {
    local AAB="$1"
    local ADD_SFX="$2"
    local CUT_SFX="$3"
    local AAB_FILE="$(basename "$AAB")"
    local AAB_DIR="$(dirname "$AAB")"
    local BASENAME="${AAB_FILE%${CUT_SFX}.*}"
    local APKS="$AAB_DIR/$BASENAME.apks"

    bundletool build-apks --bundle="$AAB" --output="$APKS" --mode=universal --overwrite
    unzip -o "$APKS" universal.apk -d "$AAB_DIR"
    mv "$AAB_DIR/universal.apk" "$AAB_DIR/$BASENAME${ADD_SFX}.apk"
    rm "$APKS"
}

./gradlew cleanAll

./gradlew bundleRelease -PABI=arm64-v8a
bundletool_universal ./build/outputs/bundle/release/transmissionbtc-*-release.aab -universal-arm64 -release
mv ./build/outputs/bundle/release/transmissionbtc-*.apk "$DEST_DIR/transmissionbtc-$VERSION-arm64-v8a.apk"

./gradlew bundleRelease -PABI=armeabi-v7a
bundletool_universal ./build/outputs/bundle/release/transmissionbtc-*-release.aab -universal-arm64 -release
mv ./build/outputs/bundle/release/transmissionbtc-*.apk "$DEST_DIR/transmissionbtc-$VERSION-armeabi-v7a.apk"

./gradlew bundleRelease -PABI=x86_64
bundletool_universal ./build/outputs/bundle/release/transmissionbtc-*-release.aab -universal-arm64 -release
mv ./build/outputs/bundle/release/transmissionbtc-*.apk "$DEST_DIR/transmissionbtc-$VERSION-x86_64.apk"

./gradlew bundleRelease -PABI=x86
bundletool_universal ./build/outputs/bundle/release/transmissionbtc-*-release.aab -universal-arm64 -release
mv ./build/outputs/bundle/release/transmissionbtc-*.apk "$DEST_DIR/transmissionbtc-$VERSION-x86.apk"
