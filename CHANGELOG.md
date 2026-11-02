# Changelog

All notable changes to Jump are documented here.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

The newest release heading must always match `version.properties`; the release
workflow refuses to publish otherwise.

## [Unreleased]

### Fixed

- Overlay open/close animations. The menu sheet, tab list and toast slide by
  their own height over 180ms with the reference
  `PathInterpolator(0.2, 0.2, 0.8, 0.8)`, with scrims that fade with the panel.
  Exits are animated now: content stays composed until the leave animation
  finishes instead of vanishing.
- The title-bar Copy / Paste / Paste & Go popup runs the reference window
  animation (`@anim/a` / `@anim/b`) in both directions: decelerating alpha
  plus a 10% rise and a 0.9→1.0 scale on the platform bounce curve.
- The site info panel dropped in with no animation; it now uses the mirrored
  popup animation from the title bar.

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
