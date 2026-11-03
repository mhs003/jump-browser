# Changelog

All notable changes to Jump are documented here.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

The newest release heading must always match `version.properties`; the release
workflow refuses to publish otherwise.

## [Unreleased]

## [1.1.0] - 2026-11-03

### Fixed

- Dark mode now darkens pages the way Chromium intends: the engine's
  algorithmic darkening plus the page's own `prefers-color-scheme` theme,
  instead of overriding every element with `!important` colours (which erased
  images and broke layouts on real sites).
- Turning Night mode off from the bottom sheet now switches both the chrome
  and the page back to light; the setting was applied before the state it read
  had been updated.
- "Ask AI about this page" and the AI settings dialog no longer crash: a
  scroll container was nested inside the dialog's own scroll region.
- New scripts are kept when added while the stored list is still loading.

### Added

- Userscripts: `==UserScript==` metadata parsing, `@match`/`@exclude`
  patterns, enable/disable and delete, run at document start and finish.
- Password manager: logins encrypted with the Android keystore, offered for
  saving after a sign-in form submits and filled automatically.
- Bookmark import and export in the standard `bookmarks.html` format, keeping
  folders.
- Full data backup and restore (settings, bookmarks, history; passwords are
  deliberately excluded).
- AI assistant: any OpenAI-compatible endpoint can be configured, and Tools ->
  "Ask AI about this page" summarises the current page.
- Orientation control: follow system, portrait, landscape or auto-rotate.
- A real Sync page explaining the local-only model instead of a dead row.

### Changed

- Release APK is 1.4 MB, down from 2.25 MB: QR scanning moved from CameraX to
  Camera2 (removing four AARs and their native libraries), Material3 replaced
  by a small local theme and ripple, unused tooling removed, x86 dropped,
  packaging noise excluded, and per-ABI splits.
- Cold start is ~0.6 s (from ~3 s): repositories load lazily, history and
  bookmarks parse off the main thread, ad blocking does set lookups instead of
  scanning every host, in-flight request tracking is bounded, and icons go
  through a downsampling cache instead of decoding during composition.

## [1.0.6] - 2026-11-02

No user-facing changes.

## [1.0.5] - 2026-11-02

### Fixed

- The release build no longer dies with "Gradle build daemon has been stopped"
  in CI: the daemon now gets enough heap for R8 and resource shrinking.
- `scripts/bump-version.sh` always adds the new changelog section, even when
  `[Unreleased]` has no entries.

## [1.0.3] - 2026-11-02

### Changed

- The release workflow now records the Gradle output in the run summary and
  uploads `release-build-log` when the build fails, so CI-only breakage can be
  diagnosed without repository admin access.

## [1.0.2] - 2026-11-02

### Fixed

- The release workflow now validates the signing secrets before building and
  reports which one is wrong, instead of failing later inside
  `packageRelease` with "keystore password was incorrect".

## [1.0.1] - 2026-11-02

### Fixed

- Floating surfaces (menu sheet, tab list, title popup, site info and
  dialogs) now use a single simple fade in and out; the earlier slide and
  scale attempts felt slow and are removed.

### Changed

- `dist/` is no longer committed; APKs are published through GitHub Releases.
- Versions come from `version.properties` and every release is recorded here.

## [1.0.0] - 2026-11-02

First release of the clean-room Jump browser.

### Added

- WebView-backed tabs with history, favicons, downloads, uploads, JavaScript
  dialogs, full-screen video and site permissions.
- Tab popup, private tabs, per-tab desktop mode and session restore.
- Native homepage with the Jump mark, search field and bookmark tiles, plus
  logo, background, opacity, search-bar and tile-colour customization.
- Inline address bar with suggestions, full-screen editing, and a title-bar
  Copy / Paste / Paste & Go long-press popup.
- Menu sheet with three pages of ten cells, reorderable entries, and
  top/bottom/slim toolbar layouts.
- Bookmarks, history records, downloads, find-in-page, reader mode, view
  source, QR scanning and site information.
- Resource sniffer, network log, saved pages, read aloud, mark-as-ad site
  exceptions, user-agent picker, orientation and fullscreen preferences.
- Reference-style dialogs, fields, checkboxes, ripples, custom toasts and
  interactive swipe-back navigation.
- Night mode via the reference page CSS.
