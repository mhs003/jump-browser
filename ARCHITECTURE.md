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

### Icon system

Chrome glyphs are stored in `ReferenceIcons.kt` as 88-unit filled outlines
traced from the reference 88px glyphs, rendered at 22dp for toolbar/slot icons
and 24dp for menu cells. This preserves the source stroke weight and exact
silhouette instead of approximating icons with Material assets. Generic symbols
that the reference does not expose as glyphs remain in `JumpIcons.kt`.

### Feedback and transient UI

Interactive chrome controls, menu cells, toolbar slots, tab rows, suggestion
rows and settings rows use the reference `#30808080` ripple, clipped by the
control's own shape. Scrims and backdrops—including the address backdrop—use an
explicit null indication so tapping outside closes the overlay without a flash.
Dialog buttons retain the same ripple.

Dialogs are native Compose dialogs rather than Material `AlertDialog`: a 288dp
card, 16dp radius, 16sp bold title, 14sp body, underlined 14sp fields, 14sp
accent actions, a 0.40 black scrim, and horizontal drag-to-dismiss. The
reference's small round checkbox is reproduced for dialog options. Short
messages go through `JumpToastHost` instead of `android.widget.Toast`: a
`#CC1E1E1E` pill with a 30dp radius, 14sp white text, optional accent action,
1.6s duration (3.5s with an action), and a 180ms 20dp rise/fall.

### UI composition

`BrowserRoot` is a single full-screen `Box` containing:

```
status inset
title bar / address bar / full-screen address editor
2dp progress overlay
WebView host + homepage/error page + suggestion dropdown + find bar
bottom bar
menu sheet (overlay)
tab popup (overlay)
library / reader / source screens (animated overlays)
reference toast host
dialogs
```

The WebView host is always present under the chrome, so opening the library or
menu does not destroy or reload the page. Overlays are full-size surfaces with
their own status-inset handling. Reader mode is rendered inside the page area,
so the title bar, page security state and bottom toolbar remain visible, as in
the reference. The address editor has two layouts: the inline 48dp row, and a
full-screen white editor that temporarily replaces the chrome while preserving
the keyboard. Settings keeps a single page stack: entering a category slides
the new page in from the right while the previous page stops at a 30% offset;
the gesture is interactive and settles at the reference 46% threshold.

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
| Settings top bar | 54dp |
| Settings title | 20sp bold |
| Settings category row | 59dp |
| Horizontal padding | 16–20dp |

The bottom bar adds the navigation-bar inset outside its 48dp content height.
The tab popup is inset by the bottom bar height, so the toolbar stays visible
and undimmed. The menu sheet is a 238dp rounded card with a 1dp outline, inset
above the navigation bar; its grid rows are 82dp with 24dp icons and 10.5sp
labels. Library headers use a 48dp toolbar, 16sp bold title, 44dp search field
and 16sp action bar labels.

## Motion

Motion is part of the product, not decoration.

| Transition | Enter | Exit |
|---|---|---|
| Address bar and suggestions | 240ms fade | 240ms fade |
| Menu scrim | 160ms fade, 20% dim | 160ms fade |
| Menu sheet | 120ms fade | 120ms fade |
| Tab popup scrim | 160ms fade, 25% dim | 160ms fade |
| Tab popup panel | 120ms fade | 120ms fade |
| Library/settings page enter | 220ms: new page from 100% right, old page to −30% left | — |
| Library/settings page back | 220ms: previous page from −30% left, current page to 100% right | — |
| Interactive page swipe | Page follows finger; 46% threshold | 300ms settle if released early |
| Dialog / popup / site info | 120ms fade | 120ms fade |
| Sheet scrim | 120ms fade | 120ms fade |
| Toast | 180ms fade + 20dp rise | 180ms fade + 20dp fall |
| Context menu | 100ms fade | 100ms fade |

Floating surfaces (menu sheet, tab list, title popup, site info, dialogs) are
real windows and animate with platform window animations defined in
`res/anim` and `res/values/styles.xml`; Compose transitions are not used for
them because they cannot play an exit once the owning boolean flips. Each
surface has its own scrim window so the dim and the surface move together. Settings and library pages use the reference
accelerate/decelerate 220ms fragment transition, including an interactive
left-to-right back gesture with the current page tracking the finger. Page
indicator and menu paging use the horizontal pager's own spring. The homepage
search gesture translates content down by 72dp and fades it to 10% before
opening the address overlay.

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

## Release packaging

Release builds enable R8 minification and resource shrinking. The signing
config intentionally reuses the local debug key so the APK can be sideloaded
for testing; replace it with a private release key before distribution. The
minified build is approximately 2.2 MB including CameraX, ZXing and AndroidX
WebKit.

## Testing

JVM tests cover URL normalization, search routing, and JSON codecs. Instrumented
tests target the Compose chrome. Manual verification uses a homepage, a real
page, the address bar, the tab popup, and the menu sheet after every chrome
change.
