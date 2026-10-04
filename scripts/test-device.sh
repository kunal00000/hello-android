#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
test_class=dev.cue.rebuild.CueFlowTest,dev.cue.rebuild.ChatRecoveryTest,dev.cue.rebuild.GestureNavigationTest
if [[ "${1:-}" == '--live' ]]; then
  test_class=dev.cue.rebuild.LiveBackendTest
  curl --fail --silent http://127.0.0.1:2002/health
  adb reverse tcp:2002 tcp:2002
fi
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
result="$(adb shell am instrument -w -e class "$test_class" dev.cue.rebuild.test/androidx.test.runner.AndroidJUnitRunner)"
printf '%s\n' "$result"
[[ "$result" == *'OK ('* ]] && [[ "$result" != *'FAILURES'* ]]
