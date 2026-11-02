# Changelog

All notable changes to Jump are documented here.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

The newest release heading must always match `version.properties`; the release
workflow refuses to publish otherwise.

## [Unreleased]

### Fixed

- Floating surfaces now animate with real platform window animations
  instead of Compose transitions that could never play their exit:
  - the menu sheet and tab list slide up over the reference's 180ms curve
    with the scrim easing alongside them, instead of using a platform
    window animation that this device's animation scale compressed;
  - the title Copy / Paste / Paste & Go popup and site info use the
    reference popup animation (fade, 10% rise, 0.9→1.0 scale, 240ms,
    platform bounce interpolator);
  - every dialog was calling `setWindowAnimations(0)`, so dialogs now use
    that same popup animation;
  - each surface has a separate scrim window that fades in 150ms without
    sliding, so the dim moves with the surface.

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
