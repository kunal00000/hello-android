# Design and interaction conventions

Cue is the visual starting point. The user explicitly approved opinionated improvements over strict parity; clarity, accessibility and honest feedback take precedence where a change helps. These principles were applied from the requested [visual-design-foundations skill](https://github.com/wshobson/agents/blob/main/plugins/ui-design/skills/visual-design-foundations/SKILL.md) and [interaction-design skill](https://github.com/wshobson/agents/blob/main/plugins/ui-design/skills/interaction-design/SKILL.md). Their CSS/React examples are translated into native Compose behavior.

## Visual foundations

`Cue` in `Primitives.kt` owns shared surface colors, typography, page insets, control/header dimensions, group radius, icon size and motion timings. Main text uses 16/24sp; compact labels use 16/20sp; captions use 14/20sp; primary headers use 18/24sp. Android font scaling remains enabled. Screen-specific dimensions reflect observed compositions and stay local instead of becoming hundreds of tokens.

Use semantic surfaces (`Page`, `Settings`, `Bubble`) instead of repeating literal colors. The neutral theme switches with appearance. The avatar palette is retained. Secondary and placeholder text are darker in light mode and lighter in dark mode. Action blue is deeper (#1B68D9) so white button text is more legible. Full accessibility conformance is not claimed; these are deliberate improvements from the reference.

`Icon` in `Icons.kt` renders native Android vectors from Hugeicons Stroke Rounded, preserving the original 24-unit viewport and 1.5-unit strokes. `Glyph` supplies semantic names; controls own accessibility labels, size and theme tint. `Avatar` composes recovered shape and face vectors. System-font metrics remain an approximation.

## Primitive boundaries

- `RoundButton`: circular visual treatment, enabled state, button role, required spoken label and press response.
- `PillButton`: action emphasis, disabled state, common capsule geometry.
- `Header`, `Group`, `SettingRow`: repeated hierarchy and alignment.
- `SearchField`: editable search semantics and labeled clear affordance.
- `EmptyState`, `TypingIndicator`: reusable empty/loading feedback.

Variants are explicit parameters. No generic component registry or multi-layer wrapper hierarchy. Layout remains in screen files.

## Interaction rules

Navigation uses forward/back horizontal translation with an outgoing parallax offset. The 320ms curve and 180ms fade are estimates: device recording confirms direction, but timings have not been derived from original source. Native Material sheets support drag/scrim/back dismissal; their physics differ from Flutter. Form sheets focus the text field and account for the keyboard.

Round buttons use an interruptible spring. Compose observes Android animation duration scaling; an observer additionally removes repeating typing pulses and press scaling when animations are disabled. The observer unregisters on disposal.

Selection controls expose their label, role and selected state. Search-clear and avatar choices have spoken names. Compose expands small clickable touch hit regions; crowded palettes still need a full TalkBack / switch-access audit. Typing and input requests expose polite live-region feedback.

Chat follows new output while the reader is at the bottom, retains position while they inspect earlier messages, and resumes following on send. Streaming can be cancelled. Failed requests show an inline explanation and retry, without opening a blocking dialog; suspended backend questions preserve request IDs and offer explicit choices or free text. Drafts, avatars, instructions, memory, group members and pending input requests are stored locally.

## Verification

Compare equivalent states on the same device, density, font scale and keyboard. Keep source and replica session data separate. Screenshots of protected picture-in-picture video contain a black rectangle, so those regions cannot be judged for parity. Compose captureToImage does not reliably capture the modal window here; modal verification uses full-device UiAutomation or adb screenshots.

A visual resemblance is not evidence of exact timing, gesture, accessibility or service parity. Track unresolved differences in PARITY.md.

## Deliberate improvements

- Settings shows actual backend health and local counts; fabricated subscription, quota and cache-success states were removed.
- Unconnected services are labeled before opening them.
- Message timestamps derive from stored creation times instead of a permanent “Now” label.
- Semantic text colors work in light and dark themes; primary and secondary pills adapt to the theme.
- Chat failure feedback stays beside the failed request. Cancelled work is not reported as a delivery failure.
- Labeled search-clear, attachment controls and selectable avatar/group choices support assistive technology.

These deviations are intentional product decisions, separate from incomplete service integrations.

Measured contrast ratios: secondary text #636361 on #F5F5F5 is 5.52:1; white on action blue #1B68D9 is 5.21:1; dark secondary text #B5B5B2 on #242424 is 7.55:1. These checks cover these token pairs, not every asset or UI state.

## Floating chat chrome

Chat controls overlay the message list. The agent header fades down over 148dp; the collapsed composer backdrop fades across 144dp, including the area behind the control. It has an 80dp lead-in above the control; question/attachment panels use a shorter 80dp fade to keep their text legible. Both use a smootherstep opacity curve with flat endpoints, avoiding abrupt fade boundaries. The measured footer height reserves scrollable space for the latest message even when attachments or question choices expand. Decorative gradients do not consume touches; the header is excluded from swipe navigation. The composer has a 1dp / 10%-ink outline, a near-white (#FAFAF9) light-mode fill and lighter charcoal (#292929) dark-mode fill and a diffuse 20dp shadow. Both fades use the active theme background.

`Cue.ChatText` is 17sp / 22sp, explicitly regular (400), with neutral tracking; the agent name is 14sp semibold. General body text remains 16sp / 24sp. Recommended future family: [Inter](https://rsms.me/inter/), regular for conversation, medium for controls and semibold for hierarchy. Font-family replacement has not been applied in this pass.

The composer refinement was checked against the installed Perplexity Android collapsed chat composer (defined outline and tonal fill) and ChatGPT Android scrolled conversation (broad backdrop fading through the composer area with a diffuse shadow). These are visual references, not reused source code. The original apps were inspected without submitting messages.

## Muse chat typography and spacing

Inspected the original installed Muse (`com.facebook.aura`, `AuraMainActivity`), not Muse Rebuild. The recovered original APK confirms `bodyRegular` at 17sp / 22sp, weight 400, bubble padding 16dp horizontal / 12dp vertical, a maximum bubble width of 85% of the screen, and gaps of 4dp within groups / 16dp between groups. Cue now uses those size, weight, padding and spacing values, with the width relative to the available chat container for adaptive layouts. Both speakers share the same width cap.

Cue groups consecutive messages from the same speaker within two minutes, excluding failed messages. This is a local approximation: Muse also considers backend correlation IDs, which Cue's current message model does not have. Cue retains the system font and neutral tracking; Muse uses Optimistic AI with slightly expanded tracking. No Muse font assets were copied. The shared chat text style also applies to the composer.

## Hugeicons

Interface glyphs use the MIT-licensed `@hugeicons/core-free-icons` 4.3.5 set, with deliberate exceptions: close and left/right chevrons retain the original simple shapes and 1.8-unit stroke in `cue_close.xml`, `cue_back.xml`, and `cue_next.xml`. The Apps catalog retains its original Gmail/Calendar/GitHub assets and colored initial tiles for other services. Settings uses a gear; group chat uses people. Avatars and selectable launcher artwork remain brand assets.

`Icons.kt` and `res/drawable/huge_*.xml` are generated by `scripts/import-hugeicons.mjs`. The script preserves the three local shape mappings and contains the single semantic-to-upstream mapping for the remaining 39 glyphs, validates the pinned package, preserves per-path strokes, converts SVG circles to paths, and rejects unsupported attributes. To regenerate, use `npm pack @hugeicons/core-free-icons@4.3.5` in a temporary directory, extract the package, and run `node scripts/import-hugeicons.mjs /path/to/package` from this repository. Format the generated Kotlin afterward. No JavaScript or icon package is required by the Android app at runtime. The MIT notice ships in `assets/licenses/hugeicons.txt`.

## Agent settings theme

Agent details uses the same settings page background, semantic text colors, standard header buttons and `Group` surfaces as app settings. Capability shortcuts use theme surfaces; Configuration, Integration and Files share the standard rounded groups and 16dp page inset. The always-charcoal gradient, forced-white content and agent-specific system-bar override were removed. Light, dark and follow-system appearance now apply consistently during navigation into and out of agent settings.

## Horizontal navigation

`SwipeNavigation` handles full-screen direction locking and cancellation. Rightward swipes go back from any non-reserved area; on Home they open Settings. No edge start is required. Leftward swipes in chat open that agent's settings. On Home, app Settings and agent settings, leftward swipes restore the next route from forward history when available. Back pushes the popped route onto that history; opening a new destination or resetting Home clears it. Restoring agent settings from chat preserves deeper forward history. History is in-memory, like the navigation stack.

Navigation commits on release after 22% of the container width (bounded to 64–96dp); an arrow follows progress and fades on release. Reversing below the threshold cancels. Vertical and multi-touch gestures do not navigate. Existing buttons and Android system back continue to work. Custom navigation is disabled while the keyboard or a root sheet/error dialog is open; other modal windows receive their own touches.

`excludeNavigationGestures()` reserves the measured bounds of chat bubbles, the composer and the chat header without consuming any of their own input. Gesture ownership is determined at the start: a drag beginning beside a bubble may navigate, while one beginning on it never does. Bubble taps/long presses remain available, and future swipe-to-reply can own that region. The header-only navigation gesture is removed; tapping the agent name still opens settings.

Child scrollers receive movement events before navigation. Apps categories use Compose `HorizontalPager`, with selected tabs following the page and taps animating to the selected category. Horizontal swipes inside the pager change categories; back remains available from its surrounding non-interactive screen area or the back button. Each page owns its list scroll position, all pages share the search query, and original app logos/tiles remain in place.

## Dropdown menus

`ActionMenu` in `Menus.kt` is the shared dropdown primitive for Home +, agent options and chat attachments. It follows the original Cue Home + and Add Skills + menus: 36dp corners, a 1dp / 8%-ink border, theme surface fill, 24dp diffuse shadow with a 6dp downward offset, 24dp horizontal padding and regular 16/20sp labels. All three use the compact 238dp width, including Home at the user's request. The Add Skills screen still opens its existing local-file picker directly; its original dropdown was a visual reference.

The popup reserves 24dp around the card so the shadow can fade beyond the surface. Rows and press feedback clip to the rounded surface; the transparent shadow margin dismisses without adding a spurious accessibility action. Selection dismisses before invoking the callback. Back and outside taps dismiss the popup. Attachments anchor above the measured composer height, including multiline drafts. The shadow uses Compose's [dropShadow API](https://developer.android.com/develop/ui/compose/graphics/draw/shadows).

## Bottom sheets and Messenger

`CueSheet` in `Sheets.kt` owns the shared bottom-sheet surface, keyboard/safe-area sizing and animated dismissal. Its visual references are original Cue Settings → Email → + and Phone → Add a phone: 40dp top corners, a circular close control, no drag handle, and a dimmed backdrop. The replica standardizes dimming at 40% black. Native Material sheet motion remains an approximation of Cue's Flutter animation. Close/actions animate downward before removing the sheet; system Back, outside taps and drag dismissal use the native sheet behavior.

Creation, group selection and avatar editors use the expanded height, capped at 90% of the window minus the keyboard. Phone setup uses 520dp and Messenger setup 420dp, capped by the same available-height calculation. Their shorter content does not require a nearly full-screen blank sheet. Navigation-bar space is reserved inside the surface. Group creation now shares the sheet surface and uses a tonal search field.

Messenger matches the original list: Telegram, LINE and Slack, in that order. These replace the previous generic Telegram/WhatsApp/Slack placeholders. Original brand assets are retained (including Slack's dark variant); `SettingRow.leading` accepts a composable for brand artwork without duplicating row layout. Each service opens a branded setup information sheet with an explicit unavailable state. The user scoped this pass to lists and setup screens; no OAuth, account binding or message delivery is implemented.
