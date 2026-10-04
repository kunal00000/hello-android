# Verification — 2026-10-04

- `:app:assembleDebug`, `:app:assembleDebugAndroidTest`, `:app:lintDebug`: passed. Lint reports zero errors and seven warnings (six dependency-update notices and Android backup-policy guidance).
- `CueFlowTest` + `ChatRecoveryTest`: 8 passed on vivo I2508, Android 16. Final run: 27.125 seconds.
- `LiveBackendTest`: 2 passed against the real Eve backend; conversation continuation and question/option/resume. Run: 66.079 seconds. Tests use separate local preference stores.
- Node 24 mobile adapter + existing memory tests: 12 passed. Includes durable event-cursor handling for input responses and upstream cancellation on disconnect.
- `git diff --check` and shell script syntax checks: passed.
- Final light/dark settings, avatar editor and keyboard-visible creation screenshots inspected. Full-device screenshot capture includes the user's PiP window; it was not stopped.

No claim of full pixel parity, original animation timing, production security, all-device compatibility, or full accessibility compliance is made. Deliberate UX improvements and incomplete services are listed separately in DESIGN.md and PARITY.md.

### Original Muse typography refinement — October 4, 2026

Inspected the installed original Muse chat and cross-checked recovered APK typography/bubble definitions. Applied 17sp/22sp regular conversation text, symmetric 85% width caps, and 4dp/16dp grouped/turn gaps. `assembleDebug`, `assembleDebugAndroidTest`, and `lintDebug` passed. Existing `CueFlowTest#navigationAndInstructionPersistence` passed on device (5.399s); its populated scrolled-chat screenshots were reviewed in light/dark mode. Captures: `cue-inspection/replica/chat-muse-typography.png` and `chat-muse-typography-dark.png` (outside this repository). This fixture covers alternating turns; same-speaker spacing is implemented but was not separately captured.

### Hugeicons replacement — October 5, 2026

Replaced custom paths and service/app glyphs with 46 native vectors generated from the MIT-licensed `@hugeicons/core-free-icons` 4.3.5 package. Build and Android lint completed with zero errors. Lint reports the seven existing dependency/backup warnings and seven long-path advisories for upstream vector geometry; original paths are preserved. All five `CueFlowTest` flows passed on the connected phone (18.702s). Reviewed chat, agent details, settings, and light/dark captures; screenshots are in sibling `cue-inspection/replica/hugeicons-*.png`. Reinstalled the debug app, retaining its saved data.

### Icon exceptions restored — October 5, 2026

Restored original Apps catalog assets/color initial tiles and the original 1.8-unit close/left/right shapes. The icon importer explicitly retains these local vectors; removed the seven unused Hugeicons resources for these exceptions and app marks. Build/lint completed without errors. The existing settings/create and navigation/instruction device flows passed (2 tests, 9.678s). Reviewed restored close/chevrons in device captures. Apps catalog restoration was checked against the prior asset and color mappings in code; a separate catalog screenshot was not obtained.

### Agent settings theme — October 5, 2026

Build and lint completed without errors. Existing navigation/instruction and avatar cancel/save tests passed on device (2 tests, 9.088s). Reviewed light/dark agent captures with shared settings surfaces; stored in sibling `cue-inspection/replica/agent-theme-light.png` and `agent-theme-dark.png`. Android test Activity system-bar styling differs from the production Activity; production bar appearance now follows the same app theme on every route.

### Softer composer and horizontal navigation — October 5, 2026

Composer fill changed to #FAFAF9 in light mode / #292929 in dark mode, with 1dp border reduced from 16% to 10% ink opacity. Reviewed populated light/dark chat captures (`cue-inspection/replica/composer-soft-*.png`).

Added reusable swipe navigation and a native Apps category pager. Four gesture tests cover short/reversed/vertical drag cancellation, edge-back, header-to-agent navigation with draft retention, keyboard suppression, bidirectional category paging, empty categories, category taps, and edge-back from the pager. All four plus the five existing UI flow tests passed on device (9 tests, 29.764s). Build and lint completed with no errors. `scripts/test-device.sh` now includes the gesture tests. System gesture navigation was not separately tested; the connected phone uses three-button navigation, and the existing Android back handler remains in place.

### Full-screen navigation and forward history — October 5, 2026

Removed edge-only and chat-header swipe navigation. Navigation now starts anywhere outside explicitly reserved chat bubble/header/composer bounds, with child horizontal/vertical scrollers taking precedence. Added an in-memory forward route stack: back retains routes, forward restores them, and opening a new destination clears the old branch. Chat restores its agent-settings route without discarding deeper forward history.

All seven revised gesture tests plus the five existing UI flow tests passed (12 tests, 30.883s). Covered non-edge swipes, short/reversed/vertical cancellation, both directions on bubbles, long-press Copy, header/composer exclusion, a gesture starting in the margin beside a bubble and crossing it, draft retention, keyboard suppression, Home/Settings forward history, branch invalidation, deeper agent-settings history, and Apps pager priority. Build/lint completed without errors; installed the updated debug app.

### Consistent compact dropdowns — October 5, 2026

Inspected the original Cue Home + and Add Skills + menus and captured both references. Consolidated Home, agent options and attachment dropdowns into `ActionMenu`, using the same compact width, rounded outline, row metrics and diffuse shadow. Attachment placement now follows measured composer height. `assembleDebug`, `assembleDebugAndroidTest`, and `lintDebug` passed; all five `CueFlowTest` flows passed (25.717s), including menu-to-create/avatar callbacks and Back dismissing attachments without leaving chat. Reviewed light/dark captures; the user's PiP video remained running and occludes part of some captures. Final captures are in sibling `cue-inspection/replica/{home,agent,attachments}-menu{,-dark}.png`. Installed the updated debug APK. Large-font and landscape layouts were not separately exercised.

### Shared sheet style and Messenger list — October 5, 2026

Inspected original Add Email (keyboard visible/hidden), Add Phone, and Messenger list. Applied their rounded sheet family through a shared `CueSheet` and matched the Telegram/LINE/Slack catalog with brand artwork and local setup information screens. No live account connections are implemented, per the user's chosen scope.

Build, Android test build and lint passed. All seven `CueFlowTest` tests passed (29.964s): existing create/group/avatar flows, all three Messenger entries, close/Done/Back dismissal retaining the underlying route, phone close and drag dismissal. After the final group surface and compact-phone sizing refinements, their targeted flows passed again (2.425s and 3.232s). Reviewed light/dark setup and phone sheets, keyboard-visible creation and avatar captures in sibling `cue-inspection/replica/`. Installed the updated debug APK. Native animation timing and system-bar rendering remain approximations; large-font and landscape checks were not performed.

### Home rightward swipe — October 5, 2026

A left-to-right swipe on Home now opens Settings; right-to-left still restores forward history. Build, test build and lint passed. The Home/Settings history and chat/draft gesture flows passed (2 tests, 4.224s), including fresh Home → Settings, Settings → Home, restoring Settings, and clearing the previous forward branch when opening Settings anew. Installed the debug build.
