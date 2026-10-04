# Parity inventory

Reference: Cue 26.17.5 (416), Android on vivo I2508. This reconstruction does not have original Dart source or authenticated Cue service APIs. The user subsequently authorized opinionated improvements. **Exact parity is neither established nor the sole design goal now.** Intentional changes are documented in DESIGN.md.

| Area | Implemented | Remaining differences / limits |
| --- | --- | --- |
| Home/navigation | Agent list, search, menus, back navigation, local pinning | No cloud sync or original account history; motion curve estimated |
| Chat | Real Eve streaming, persistent session continuation, drafts/history, stop, errors/retry, long-press copy | Text rendering lacks rich markdown/tool-result layouts; reconnect and partial-response reconciliation need work |
| Agent input | Typed questions and explicit option responses resume Eve requests | Live question/option flow verified; tool-approval-specific layouts remain unverified |
| Attachments | Photos/files, camera preview, contact name, last-known coarse location, private copies | Camera preview is low resolution; no cloud file browser, location selection map or rich preview parity |
| Agent settings | Rename, instructions, local memory, avatar face/shape/color save/cancel | Local memory is sent as per-turn context, not a synchronized view of Eve's Drive memory |
| Creation | Agent form, autofocus, validation, avatar editor; search/select local group members | Group uses a single Eve session with member context, not independent agent routing; QR provisioning absent |
| Appearance | Light/dark/system themes, launcher icon aliases, language preference | Dark reference not fully inspected; language affects agent context, not UI translation |
| Apps/skills | App catalog and local skill text import/remove | OAuth account linking and connector execution absent; catalog incomplete; some providers use colored initial tiles |
| Mail/computer/phone/payment | Observed navigation/layouts and clear unavailable messages | Mail delivery, remote desktop/terminal UI, virtual phone and payments not provisioned |
| Account/subscription | Local display-name editing and reference settings layout | No real account/auth, billing, push notifications or subscription entitlements; copied quota display replaced with actual local counts |
| Visual system | Recovered avatar/launcher assets, Hugeicons interface vectors, shared tokens/primitives | Icon family deliberately replaced, except close/chevrons and app-list marks; fonts, sheet depth/physics, spacing and motion differ; no pixel-diff proof |
| Accessibility | Control labels/roles, selection state, live loading feedback, reduced-motion behavior | Secondary-text contrast improved; full large-font, TalkBack and switch-access audit outstanding |

## Evidence and tests

Reference screenshots and the navigation recording are in the sibling `cue-inspection/screens` directory. Original account screenshots are deliberately not committed here. The device's video remained running in PiP during the final tests.

- 8 device checks: flow instrumentation covers navigation/instructions, avatar cancel/save, memory/search, agent creation validation and group member selection; recovery tests cover partial streams, cancellation and persisted drafts/questions.
- 2 live device tests: session continuation and agent question → option selection → resumed response.
- 8 adapter tests: stream framing, continuation, cancellation, validation, attachment conversion, input-request/response IDs upstream failures and client-disconnect cancellation.
- 4 existing backend memory regression tests.
- Android build and lint. Dependency update notices and remaining lint warnings are not treated as proof of visual quality.

Provisioning unavailable services and validating unobserved states are required before this can be called a complete replica. The current API is deliberately local development only.
