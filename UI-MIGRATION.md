# UI migration — 2026-11-09

Reference: `C:/Users/chenc/Desktop/UI-DESIGN-SPEC.md` and the sibling `YINU` source.
The specification is a visual reference, not authority to add YINU business features.

## Contract

Do not change repositories, models, DataStore keys, defaults, download workers, parsers,
permissions, route identifiers, share/shortcut handling or back-stack semantics for UI work.
Existing theme choices keep their persisted IDs. No forced reset or data migration.

## Stage 1 — implementation

`ui/designsystem/LinkGrabUi.kt` owns theme roles, spacing, card and preference row.
Migrated screens: SettingsScreen, UpdateSettingsScreen, DownloadsScreen.
Their original callback expressions, state collection and navigation entry points remain.
Legacy helpers bridge to the new palette only inside the migrated presentation subtree.
Top chrome uses 24dp real scroll, 250ms alpha and 12dp blur. Sampling excludes chrome.

The cards are an interim native Compose implementation, not a claim of exact MiuiX
Card reuse. The source fork, shaders, theme bridge and licensing must be migrated together
in the chrome/overlay stage. Runtime shader parity and short-page minimum content height
are not yet implemented. Existing style previews remain compatibility previews.

## Stage 2 — 2026-11-13

Regular navigation destinations use the shared theme. The home, history and batch
screens reuse its cards, input and filter styles through their existing components.
Result and tool screens reserve the same header height as the shared chrome. The
history delete and live-photo save dialogs use the bottom-aligned dialog shell.
Image preview returns to the originating thumbnail when it still represents the
current image. Other dismissals fade in place. Selection, saving and player callbacks
remain on the existing handlers.

The onboarding screen and native WebView chrome retain their existing layout.
Saved appearance and bottom-bar options still select their existing motion and
component variants. Bottom dock and IME coordination, short-page scroll, exact
MiuiX components, progressive shaders, and full overlay animation remain pending for that version.

## Version 3.4.2

The app root selects Liquid Glass (0) or Soft Glass (1). Legacy preferences 1 and 2
both resolve to Soft Glass through the existing preference key. Liquid Glass uses
the original iOS palette, refraction and navigation. Soft Glass applies the design
roles throughout the app, with continuous cards, soft menu blur, bottom dialogs,
the 600/320 ms page transition, and floating dock blur and IME visibility.

The progressive blur uses a local separable Android shader on API 33+ and a masked
mirror blur on API 31–32. It follows the reference sizing and tint parameters.
Native WebView content and the user's bottom bar variant remain independent.

## Version 3.4.3

Soft Glass uses YINU's radial sampling and denoising shaders, adapted to the app's
Android backdrop API. Shader source retains the Haooz copyright and AGPL-3.0-only
notice. See `licenses/YINU-AGPL-3.0.txt` and the NexioSchedule snapshot
`35503185bc42bf456c03a4c59dea0d22b2b1d681` cited in the design specification.
Removed theme implementation files and the HyperOS dock option. A saved HyperOS
dock resolves to the default dock. Other dock IDs are unchanged.

## Regression gates for each stage

- Compare function callbacks, route names and business-layer diff against the baseline.
- Run existing history, media and signer tests; compile signed release, verify certificate.
- Device: launch, all three tabs, each modified entry, system/app back, repeated open/close.
- Light/dark/system theme, large fonts, long values, narrow/wide window, IME and navigation insets.
- Settings toggles persist after restart; history/download queue survive an in-place APK update.
- Download pause/resume/cancel and notification permission denial retain their behavior.
- Header: scroll past 24dp, release, wait for bounce, material persists; return to top hides it.

No connected device has yet validated the new visuals. Compilation is not runtime or
frame-time evidence. Record actual verification results at delivery.
