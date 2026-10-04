# Cue Android replica

Native Kotlin / Jetpack Compose reconstruction of the observed Cue 26.17.5 Android interface, based on [hello-android](https://github.com/kunal00000/hello-android). Installs separately as **Cue Replica** (`dev.cue.rebuild`).

This is a working local replica with real Eve conversations and user-approved usability improvements. It is **not complete feature or pixel parity**. See [the parity inventory](docs/PARITY.md) for the specific gaps and [design decisions](docs/DESIGN.md) for the primitive and interaction conventions.

## Run

Requires JDK 17, Android SDK 35, Node 24, pnpm, an authorized USB Android device, and a configured sibling `../superintelligence` checkout. The existing backend's model / sandbox credentials stay on the Mac.

```sh
source "$HOME/.config/android-development/env.sh"
# In a separate terminal with Node 24 on PATH:
./scripts/run-backend.sh
# Build, install, forward localhost, and launch:
./scripts/device.sh
```

`CUE_BACKEND_DIR` can point at a different superintelligence checkout. The mobile adapter is `../superintelligence/mobile/server.mjs`; it talks to Eve at port 2001 and serves NDJSON at port 2002. The phone reaches it through `adb reverse tcp:2002 tcp:2002`. Re-run forwarding after reconnecting USB. This is a local development setup, with no public server or account authentication.

To start the services manually in two terminals:

```sh
cd ../superintelligence
pnpm exec eve dev --no-ui --no-default-extensions --port 2001
# Second terminal, same directory, Node 24:
node mobile/server.mjs
```

## Build and check

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
./scripts/test-device.sh
# Requires the running backend; checks conversation continuity and a real agent question:
./scripts/test-device.sh --live
# From superintelligence with Node 24:
node --test mobile/server.test.mjs tests/*.test.ts
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.

Device tests use separate preference stores. Tests launch the replica in the foreground; they do not stop any other application. Visual checks were performed on a vivo I2508 at 1260×2750 / 520 dpi with video in picture-in-picture; the protected video appears black in captures.

## Code map

All application code is under `app/src/main/java/dev/cue/rebuild`:

- `Primitives.kt`: theme tokens, icons, original SVG avatars, buttons, headers, rows, search and loading states.
- `MainActivity.kt`: system bars, navigation transitions and modal presentation.
- `CueState.kt`: agent and conversation state, private local persistence and streaming transport.
- `ChatScreens.kt`: agent list, search entry points, conversation and composer.
- `AgentScreens.kt`: agent details, instructions, memory, avatar and creation flows.
- `SettingsScreens.kt`: settings and supporting capability screens.
- `Attachments.kt`: system pickers, private attachment copies and safe file opening.

Add a shared primitive when repeated behavior or a stable UI responsibility warrants it. Screen-specific composition stays with its screen. There is no parallel web stack, generic schema renderer, DI framework or duplicate transport model layer.

## Assets

Some reference assets were extracted from the installed Cue APK for this local reconstruction. They remain third-party assets; this repository does not establish a redistribution license. See [reference provenance](docs/REFERENCE.md). No original Cue source code or service credentials were available.
