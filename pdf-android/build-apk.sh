#!/usr/bin/env bash
# Compila o APK sem Gradle, usando as ferramentas do Android SDK
# (aapt2, javac, d8/dx, zipalign, apksigner).
# Uso: ./build-apk.sh            (gera release/PDFLeitor.apk)
# Variáveis: ANDROID_JAR, BUILD_TOOLS, KEYSTORE, KS_PASS
set -euo pipefail
cd "$(dirname "$0")"

ANDROID_JAR="${ANDROID_JAR:-/usr/lib/android-sdk/platforms/android-23/android.jar}"
BUILD_TOOLS="${BUILD_TOOLS:-/usr/lib/android-sdk/build-tools/debian}"
KEYSTORE="${KEYSTORE:-keystore/debug.keystore}"
KS_PASS="${KS_PASS:-android}"
PACKAGE=br.pdfleitor.app
VERSION_CODE=1
VERSION_NAME=1.0
SRC=app/src/main
OUT=build
mkdir -p "$OUT" release
rm -rf "$OUT/classes" "$OUT/gen" "$OUT/res.zip" "$OUT"/*.apk "$OUT/classes.dex"
mkdir -p "$OUT/classes" "$OUT/gen"

echo "[1/5] Recursos (aapt2)"
# o manifesto-fonte não declara package/uses-sdk (padrão do Gradle/AGP 8); são injetados aqui
sed "s|<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">|<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" package=\"$PACKAGE\">|" \
    "$SRC/AndroidManifest.xml" > "$OUT/AndroidManifest.xml"
"$BUILD_TOOLS/aapt2" compile --dir "$SRC/res" -o "$OUT/res.zip"
"$BUILD_TOOLS/aapt2" link -o "$OUT/base.apk" -I "$ANDROID_JAR" --manifest "$OUT/AndroidManifest.xml" \
    --java "$OUT/gen" --auto-add-overlay "$OUT/res.zip" --rename-manifest-package "$PACKAGE" \
    --min-sdk-version 21 --target-sdk-version 34 --version-code "$VERSION_CODE" --version-name "$VERSION_NAME"

echo "[2/5] Java (javac)"
find "$SRC/java" "$OUT/gen" -name '*.java' > "$OUT/sources.txt"
if ! javac -nowarn -encoding UTF-8 -source 8 -target 8 -bootclasspath "$ANDROID_JAR" -classpath "$ANDROID_JAR" \
    -d "$OUT/classes" @"$OUT/sources.txt" > "$OUT/javac.log" 2>&1; then
    grep -v -E '^warning: \[options\]|^[0-9]+ warnings?$|Picked up' "$OUT/javac.log"; echo "Falha na compilação Java"; exit 1
fi

echo "[3/5] DEX"
if command -v d8 >/dev/null 2>&1; then
    d8 --min-api 21 --lib "$ANDROID_JAR" --output "$OUT" $(find "$OUT/classes" -name '*.class')
else
    "$BUILD_TOOLS/dx" --dex --min-sdk-version=21 --output="$OUT/classes.dex" "$OUT/classes"
fi

echo "[4/5] Empacotando"
cp "$OUT/base.apk" "$OUT/unsigned.apk"
(cd "$OUT" && zip -q -X unsigned.apk classes.dex)
"$BUILD_TOOLS/zipalign" -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

echo "[5/5] Assinando"
if [ ! -f "$KEYSTORE" ]; then
    mkdir -p "$(dirname "$KEYSTORE")"
    keytool -genkeypair -keystore "$KEYSTORE" -storepass "$KS_PASS" -keypass "$KS_PASS" -alias pdfleitor \
        -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=PDF Leitor, O=PDF Leitor, C=BR"
fi
"$BUILD_TOOLS/apksigner" sign --v4-signing-enabled false --ks "$KEYSTORE" --ks-pass "pass:$KS_PASS" --out release/PDFLeitor.apk "$OUT/aligned.apk"
"$BUILD_TOOLS/apksigner" verify release/PDFLeitor.apk
echo "OK: release/PDFLeitor.apk ($(du -h release/PDFLeitor.apk | cut -f1))"
