# Cue Android replica

Native Kotlin / Jetpack Compose reconstruction of the observed Cue 26.17.5 Android interface, based on [hello-android](https://github.com/kunal00000/hello-android). Installs separately as **Cue Replica** (`dev.cue.rebuild`).

This is a working local replica with real Eve conversations and user-approved usability improvements. It is **not complete feature or pixel parity**. See [the parity inventory](docs/PARITY.md) for the specific gaps and [design decisions](docs/DESIGN.md) for the primitive and interaction conventions.

## Run

Requires JDK 17, Android SDK 35, and an authorized Android device for installation.
The default build connects to the deployed private backend over HTTPS; USB and a
running Mac backend are not needed after installation.

```sh
source "$HOME/.config/android-development/env.sh"
./scripts/device.sh
```

In Settings → Agent connection → Connect backend, enter the personal access token
once. It is validated online and stored encrypted with Android Keystore. The
Manage connection screen supports replacement and disconnect. Tokens are never
included in the APK. Existing local chat history remains on the phone.

Production backend setup, token rotation, quotas, and private uploads are documented
in the sibling [MOBILE_DEPLOYMENT.md](../superintelligence/MOBILE_DEPLOYMENT.md).

Chat bubbles render Markdown for both speakers. The composer’s **Aa** control opens
selection-aware formatting shortcuts and a preview using the same renderer. Return
continues lists and quotes; Return on an empty item exits them. Ctrl+B, Ctrl+I, and
Ctrl+K work with a hardware keyboard. Drafts, sent messages, and message copying keep
the original Markdown source. Code blocks have a separate **Copy code** action.
Rendering uses [Multiplatform Markdown Renderer](https://github.com/mikepenz/multiplatform-markdown-renderer)
0.33.0, compatible with this app’s Kotlin and Compose versions.

Page swipes commit after 8% of the screen width; a deliberate reverse flick still
cancels. Message bubbles, composer controls, and horizontal content scrollers retain
their own gestures.

For optional loopback development, use Node 24 and the sibling backend checkout:

```sh
# Separate terminal:
./scripts/run-backend.sh
# Build a debug-only local variant:
./scripts/device.sh -PcueBackendUrl=http://127.0.0.1:2002
adb reverse tcp:2002 tcp:2002
```

`CUE_BACKEND_DIR` can select a different backend checkout. The loopback adapter runs
at port 2002 and calls Eve on port 2001. Release builds always use production HTTPS.

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
node --test mobile/*.test.mjs tests/*.test.ts
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.

Device tests use separate preference stores. Tests launch the replica in the foreground; they do not stop any other application. Visual checks were performed on a vivo I2508 at 1260×2750 / 520 dpi with video in picture-in-picture; the protected video appears black in captures.

## Code map

All application code is under `app/src/main/java/dev/cue/rebuild`:

- `Primitives.kt`: theme tokens, icons, original SVG avatars, buttons, headers, rows, search and loading states.
- `MainActivity.kt`: system bars, screen composition and modal presentation.
- `SwipeNavigation.kt`: interactive adjacent-page dragging and settling, shared by gesture and button navigation.
- `CueState.kt`: agent and conversation state, private local persistence and streaming transport.
- `ChatScreens.kt`: agent list, search entry points, conversation and composer.
- `AgentScreens.kt`: agent details, instructions, memory, avatar and creation flows.
- `SettingsScreens.kt`: settings and supporting capability screens.
- `Attachments.kt`: system pickers, private attachment copies and safe file opening.

Add a shared primitive when repeated behavior or a stable UI responsibility warrants it. Screen-specific composition stays with its screen. There is no parallel web stack, generic schema renderer, DI framework or duplicate transport model layer.

Navigation has a stable spatial order: **Settings ↔ Home ↔ last-opened Chat ↔ Agent settings**.
Home remembers the selected chat across visits to Settings and app restarts; until a chat has
been opened, Home has no right-hand neighbor. Deeper screens retain normal back/forward
history. `CueState` chooses neighboring routes and transition direction; `SwipeNavigation`
renders both pages during a drag, commits after settling, and returns a cancelled drag to
its origin. Bubbles, the composer, the keyboard, and child horizontal pagers retain their
own gestures. Saved page state preserves scroll positions between visits.

## Assets

Some reference assets were extracted from the installed Cue APK for this local reconstruction. They remain third-party assets; this repository does not establish a redistribution license. See [reference provenance](docs/REFERENCE.md). No original Cue source code or service credentials were available.

## Integrations

Settings → Integrations includes the 94 entries from Eve's directory, grouped by
category, plus eight device integrations. Search and the Connected tab retain
state when returning from a detail screen. Provider logos retain their brand
artwork; app controls use the existing Hugeicons and simple navigation glyphs.

`IntegrationCatalog.kt` owns catalog metadata, `IntegrationsScreen.kt` the shared
catalog/detail/account controls, and `DeviceAccess.kt` the Android permission and
execution boundary. `DeviceRequestCard.kt` handles per-request consent and bounded
result delivery. Reuse the existing `Group`, `SettingRow`, `CueSheet`, `ActionMenu`
and `PermissionRow` primitives rather than introducing provider-specific screens.

Device connections are local to this installation and default to Ask. Connecting requests the required Android
permission before enabling access. Manage opens system settings afterward, and
permissions are checked again when a tool executes. Tools run
only while the chat is active and the activity is resumed. Reads are bounded to
50 records (Health Connect exposes step records); notifications are currently
active notifications, and location includes the last-known timestamp. Draft
writes open the appropriate device app for the user to finish. No SMS is sent
and no call is placed automatically. Disconnect resets policies and clears the
queued retry result; it does not revoke Android permissions or erase data already
sent to the backend. Results may be retained in backend conversation history.

OAuth accounts use the authenticated backend's `/integrations` API. The phone
never stores provider credentials. Connected tools have per-account Allow / Ask /
Deny controls; sign-in returns through `cue://integrations`. Channels, extensions,
memory and observability show their setup guides until configured on the backend.

Refresh the bundled Eve directory with `python3 scripts/import-integrations.py`.
Source descriptions and brand artwork come from <https://eve.dev/integrations>;
logos belong to their respective owners. Refresh backend connection descriptors
with `python3 scripts/import-connection-catalog.py` in `../superintelligence`.

`IntegrationsTest` verifies catalog assets, filtering/navigation, setup policies,
disconnects, fail-closed execution, and UI consent. `DeviceProductionTest` is
explicitly opt-in (`-e liveProduction true`): it requests an empty dialer through
the deployed agent and verifies the result round trip without making a call.

Every outgoing message refreshes the agent's device capability metadata and phone
time zone. This includes connection/policy/OS grant state, not device records.
The backend uses it to route natural-language requests and explain unavailable
access; Android still enforces permissions when the requested tool runs.
