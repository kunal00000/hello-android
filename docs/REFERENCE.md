# Reference provenance

- Starter: https://github.com/kunal00000/hello-android, starting commit f92aee (see repository history for full hash).
- Device reference: installed Android package `ai.manus.agents`, version 26.17.5, version code 416.
- APK SHA-256: `8ddfc4c181f45cc4a0fcb581bd1f31676eb56e1f7b556dc3bba8ff418857bb95`.
- Original Flutter assets were read from `assets/flutter_assets`; selected vectors, launcher images and the computer wallpaper are included under `app/src/main/assets/cue` and drawable resources.
- Original Dart application source was not available. The native implementation is a reconstruction from observed screens and state changes, not a source port.
- Inspection files remain outside this repository in `../cue-inspection`; do not publish account screenshots or the pulled APK as part of a source release.
- Reference assets retain their owners' rights. No redistribution permission is asserted by this local reconstruction.

The local `../dev-skills` guidance informed the small component vocabulary, shared ownership of state, and avoidance of speculative frameworks. The two requested wshobson design skills are linked in DESIGN.md with the decisions they informed.

## Preferred dropdown references — October 5, 2026

Inspected original Cue Home + and Settings → Skills → Add Skills + on the connected phone. Captures: `../cue-inspection/screens/home-plus-menu.{png,xml}` and `add-skills-menu.{png,xml}`. Both have approximately 36dp corner radii, a pale outline and diffuse shadow; item bounds are 143 physical pixels high at 520dpi (44dp). Home is approximately 300dp wide and Skills 238dp. The replica deliberately uses the compact 238dp width everywhere, as requested. These replace the older agent-overflow and attachment-menu treatments as the style reference.

## Preferred sheet and Messenger references — October 5, 2026

Inspected original Cue Settings → Email → + with the keyboard visible and hidden, and Settings → Phone → Add a phone. Saved `../cue-inspection/screens/add-email-sheet.png`, `add-email-sheet-no-keyboard.png`, and `add-phone-sheet.png` with XML where available. These are the preferred sheet family, replacing Create Agent's narrower-corner stacked-sheet treatment as the styling reference. The original Email and Phone backdrops differ in opacity; the replica deliberately standardizes them.

Agent → Messenger lists Telegram, LINE and Slack. Reference: `../cue-inspection/screens/messenger-list-ref.png`. Telegram's original Connect action opens an external Telegram binding draft; no message was sent, and the generated draft was removed. The replica uses a local unavailable setup screen for now, as requested, and never launches that original binding flow.
