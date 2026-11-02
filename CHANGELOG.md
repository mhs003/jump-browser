# Changelog

All notable changes to Jump are documented here.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

The newest release heading must always match `version.properties`; the release
workflow refuses to publish otherwise.

## [Unreleased]

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
