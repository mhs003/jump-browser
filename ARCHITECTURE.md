# Architecture

## Goals

Jump deliberately separates three concerns that are commonly entangled in
Android browsers:

1. **Engine** — WebView lifecycle, navigation, requests, downloads, permissions.
2. **State** — tabs, history, bookmarks, settings, downloads, persistence.
3. **Presentation** — the Compose chrome, homepage, overlays, and screens.

The result is small, testable, and easy to extend without touching the WebView
plumbing for every UI change.

## Layers

```
┌─────────────────────────────────────────────────────────┐
│ Compose UI                                              │
│ BrowserRoot · Chrome · HomePage · LibraryScreens · ...  │
└───────────────▲───────────────────────────┬─────────────┘
                │ TabInfo / state           │ intents
┌───────────────┴───────────────────────────▼─────────────┐
│ BrowserController                                       │
│ WebView sessions · navigation · interception · extras    │
└───────────────▲───────────────────────────┬─────────────┘
                │ models                    │ reads/writes
┌───────────────┴───────────────────────────▼─────────────┐
│ Repositories                                            │
│ Settings · Bookmarks · History · Downloads · TabStore    │
│ SharedPreferences JSON · DownloadManager                │
└─────────────────────────────────────────────────────────┘
```

### `AppContainer`

Application-scoped singleton created in `JumpApp`. Holds the repositories, a
`SupervisorJob` coroutine scope, and the app files directory. It is passed into
the activity and down into Compose; nothing reaches for a global singleton.

### `BrowserController`

One controller per activity. It owns:

- a `LinkedHashMap<Long, Session>` of tabs;
- one `WebView` per tab, created lazily;
- the active tab and a `FrameLayout` host supplied by Compose;
- `TabInfo` snapshots published as Compose state.

It creates WebViews with a shared configuration pipeline:

- JavaScript, DOM storage, database storage, zoom, file/content access;
- mobile or desktop user agent;
- cookie policy, including optional third-party cookie blocking;
- algorithmic darkening and a CSS fallback for older WebViews;
- `ViaClient` for URL overrides, ad interception, favicon/title/progress,
  history recording, and SSL rejection;
- `ViaChromeClient` for file chooser, permission requests, JS dialogs,
  full-screen video, and geolocation;
- download interception;
- long-press hit testing for link and image context menus.

Navigation intents are normalized by `UrlUtils` and routed into the active tab.
When the active tab is the native homepage, the first navigation reuses that
tab and records a virtual homepage entry so the back button can return home.

WebView instances are detached and re-attached when tabs switch. They are never
recreated for ordinary UI changes.

### Persistence

All repositories publish immutable lists through Compose `mutableStateOf`.

- **Settings** — a single JSON object in `via_clone_prefs`.
- **Bookmarks** — JSON array; bookmark favicons are cached as PNG files under
  `files/bookmark_icons`.
- **History** — JSON array capped at 3,000 entries; repeated visits within two
  minutes coalesce.
- **Tabs** — non-incognito URL snapshots plus the active index. Restored tabs
  are created without WebViews and hydrated when selected.
- **Downloads** — DownloadManager IDs plus display metadata; progressive state
  is queried from DownloadManager on a lightweight polling coroutine.

### UI composition

`BrowserRoot` is a single full-screen `Box` containing:

```
status inset
title bar / address bar
2dp progress overlay
WebView host + homepage/error page + suggestion dropdown + find bar
bottom bar
menu sheet (overlay)
tab popup (overlay)
library / reader / source screens (animated overlays)
dialogs
```

The WebView host is always present under the chrome, so opening the library or
menu does not destroy or reload the page. Overlays are full-size surfaces with
their own status-inset handling.

## Layout rhythm

| Element | Value |
|---|---|
| Title bar | 48dp |
| Title text | 20sp |
| Chrome icon | 24dp |
| Bottom bar | 48dp |
| Bottom slots | 5 equal weights |
| Menu sheet | 5 columns × 2 rows, 304dp total |
| Tab popup row | 48dp |
| Library top bar | 48dp |
| Horizontal padding | 16–20dp |

The bottom bar adds the navigation-bar inset outside its 48dp content height.
The tab popup is inset by the bottom bar height, so the toolbar stays visible
and undimmed. The menu sheet includes the navigation inset and covers the
toolbar, exactly like the reference experience.

## Motion

Motion is part of the product, not decoration.

| Transition | Enter | Exit |
|---|---|---|
| Address bar swap | 130ms fade + 150ms 1/12 slide | 90ms fade + 120ms slide |
| Suggestion dropdown | 120ms fade + 180ms slide | 90ms fade + 120ms slide |
| Menu scrim | 180ms fade | 150ms fade |
| Menu sheet | 220ms slide-up + 120ms fade | 170ms slide-down + 100ms fade |
| Tab popup scrim | 170ms fade | 140ms fade |
| Tab popup panel | 210ms slide-up + 100ms fade | 160ms slide-down + 80ms fade |
| Library screens | 230ms slide + 160ms fade | 190ms slide + 120ms fade |
| Context menu | 100ms fade | 100ms fade |

Easing is decelerating (`FastOutSlowInEasing`) for movement and linear for
fades. Page indicator and menu paging use the horizontal pager's own spring.

## Extension points

- **New menu action** — add a `MenuAction`, a `MenuEntry`, handle it in
  `BrowserRoot`.
- **New browser capability** — add a method to `BrowserController`; keep it
  independent of Compose.
- **New persisted model** — add a JSON codec in `Models.kt` and a repository in
  `Repositories.kt`.
- **New screen** — compose it over the live browser surface in `BrowserRoot`;
  use `ScreenScaffold` for a consistent 48dp header.
- **QR decoding** — `QrScannerScreen` binds CameraX to the lifecycle, analyzes
  luminance frames with ZXing, and feeds decoded URLs through
  `BrowserController.navigate`. The clipboard fallback handles cameras that are
  unavailable.
- **Real incognito isolation** — use AndroidX WebKit profiles on providers that
  support them; keep the existing tab flag for history suppression.

## Testing

JVM tests cover URL normalization, search routing, and JSON codecs. Instrumented
tests target the Compose chrome. Manual verification uses a homepage, a real
page, the address bar, the tab popup, and the menu sheet after every chrome
change.
