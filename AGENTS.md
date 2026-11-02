# AGENTS.md

Guidance for coding agents working in the Jump repository.

## Identity

- Product name: **Jump**.
- Application ID and namespace: `com.jump.browser`.
- Module directory: `app/`.
- The project is a clean-room implementation of the Via browser experience.
  Do not copy Via source, binaries, or artwork into this repository.

## Commands

Everything is built on-device with TAD inside Termux.

```bash
tad doctor               # verify the toolchain
tad compile debug        # fast Kotlin/type check
tad build debug          # assemble and verify an APK
tad install              # install the built APK
tad test unit            # JVM tests
tad test instrumented    # on-device Compose tests
```

After any on-device check, return to Termux:

```bash
adb -s 127.0.0.1:5555 shell am start \
  -n com.termux/com.termux.app.TermuxActivity
```

## Conventions

- Kotlin only. UI is Jetpack Compose with Material 3 primitives, but colors,
  typography, spacing and motion are defined by Jump rather than stock Material.
- Do not add Material icon dependencies. Icons live in `ui/JumpIcons.kt` as
  hand-built `ImageVector`s.
- Keep `BrowserController` free of Compose imports. It owns WebView sessions and
  publishes immutable `TabInfo` snapshots through Compose state.
- Persistence is JSON over `SharedPreferences` in `data/Repositories.kt`.
  Writes are asynchronous where they occur off the main thread.
- All screens are composed over the live browser surface. Do not dispose the
  WebView host when opening the library, menu or overlays.
- Respect the 48dp chrome rhythm: 48dp title bar, 48dp bottom bar, 48dp rows.
- The five-slot bottom bar is fixed: back, forward, home, tab count, menu.
- Animations use the timings documented in `ARCHITECTURE.md`. Keep 220–240ms
  for panels, 150–180ms for fades, and decelerating easing for entrance.
- Do not introduce references to files outside this repository in code or
  documentation.

## Layout

```
app/src/main/java/com/jump/browser/
  MainActivity.kt
  JumpApp.kt
  browser/BrowserController.kt
  browser/UrlUtils.kt
  browser/AdBlocker.kt
  data/Models.kt
  data/Repositories.kt
  ui/BrowserRoot.kt
  ui/Chrome.kt
  ui/HomePage.kt
  ui/LibraryScreens.kt
  ui/Extras.kt
  ui/Components.kt
  ui/JumpIcons.kt
  ui/Suggestions.kt
  ui/theme/Theme.kt
```

## Change discipline

- Build after structural changes; a successful Kotlin compile is the minimum
  bar, but always install and exercise the affected screen.
- Test with both a homepage tab and a real web page.
- When changing the chrome, verify the homepage, a loaded page, the address bar,
  the tab popup, and the menu sheet.
- Keep commits coherent and descriptive.
- Release builds are minified, resource-shrunk and signed with the local debug
  key for sideloading: `tad build release`.
- Keep `dist/Jump-1.0.0.apk` and `dist/SHA256SUMS.txt` in sync with the release
  build when producing a user-facing artifact.
- Screenshots live in `docs/screenshots/`; README references only files inside
  this repository.
