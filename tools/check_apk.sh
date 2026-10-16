#!/usr/bin/env bash
# Smoke check for a built APK: it must be ours, and it must not ask for the network.
# Uses aapt2 from the Android SDK build tools (present on GitHub's Ubuntu runners and on dev machines).
#   tools/check_apk.sh app/build/outputs/apk/debug/app-debug.apk
set -euo pipefail

apk="${1:?usage: check_apk.sh <apk>}"
aapt2="$(find "${ANDROID_HOME:?ANDROID_HOME is not set}/build-tools" -name 'aapt2*' -type f | sort -V | tail -1)"
[ -n "$aapt2" ] || { echo "aapt2 not found under $ANDROID_HOME/build-tools"; exit 2; }

badging="$("$aapt2" dump badging "$apk")"
echo "$badging" | grep -E "^(package|sdkVersion|targetSdkVersion|application-label):"

if echo "$badging" | grep -q "uses-permission: name='android.permission.INTERNET'"; then
  echo "FAIL: $apk declares INTERNET"
  exit 1
fi
echo "$badging" | grep -q "package: name='io.github.maborokun.docscanner'" || { echo "FAIL: unexpected package"; exit 1; }
echo "OK: $apk"
