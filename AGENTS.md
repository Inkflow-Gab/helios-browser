# AGENTS.md

Helios Browser — single-module Android app (`:app`), Kotlin + Jetpack Compose + AndroidX WebKit.
Entry point: `MainActivity.kt` -> `setContent { HeliosApp() }`, which shows first-run setup or the
browser depending on persisted state.

## Build & verify

- **GitHub Actions is the build.** `.github/workflows/build.yml` runs on push/PR to `main` in three
  jobs: `adblock-engine` (Rust 1.98.0 + NDK 27.3, `cargo ndk` for arm64-v8a/armeabi-v7a/x86_64,
  uploads `helios-jniLibs`), `rust-tests` (`cargo test` in `native/helios-adblock`), then `build`
  (downloads the `.so` files into `app/src/jniLibs`, verifies all three ABIs are present, then
  `testDebugUnitTest` + `assembleDebug`). The APK job **fails** if any ABI is missing, so a red
  engine build blocks the APK rather than shipping one with no ad blocking.
- **Never build on the dev machine.** The host is a low-end phone: no `ANDROID_HOME` /
  `ANDROID_SDK_ROOT`, no `local.properties` (gitignored), and Gradle/Kotlin compiler runs are
  heavy enough to be a problem on the device. Report changes as "compiles pending CI".
  (`kotlinc` and a stray `android.jar` do exist under `/tmp/opencode`, but do not use them —
  same reason.)
- `gradlew` is committed with mode 755 but lands as `0660` on this sdcard checkout, so `./gradlew`
  gives "Permission denied". This is moot given the rule above.
- Toolchain (pinned in `build.gradle.kts` / `gradle-wrapper.properties`): Gradle 8.7, AGP 8.5.0,
  Kotlin 2.0.0 with the separate `org.jetbrains.kotlin.plugin.compose`, KSP 2.0.0-1.0.22
  (KSP1 — do **not** set `ksp.useKSP2`), Hilt 2.52, Room 2.6.1, DataStore 1.1.1,
  compileSdk/targetSdk 34, minSdk 26, Java 17 source/target.
  **No version catalog** — dependency versions are literals in `app/build.gradle.kts`.
  Release builds have `isMinifyEnabled = false`.
- Room exports its schema to `app/schemas` via the `ksp { arg("room.schemaLocation", ...) }` block.
  Commit that JSON when the schema changes so migrations can be diffed.
- `settings.gradle.kts` deliberately has **no** `content { includeGroup... }` filters. Adding one
  back can route a plugin marker to a repo that 404s for it (that is what broke Hilt resolution
  during the rewrite).
- Don't commit `Helios-Browser-v0.1.0-beta.apk` from the repo root — untracked 8 MB build artifact.

## Testing

- `app/src/test` exists and is real: JUnit 4 only, plain JVM, **no Robolectric**, no
  `src/androidTest`. CI runs `testDebugUnitTest`. Report results honestly — nothing runs locally.
- Keep new logic JVM-testable. The rule that makes this work: anything testable must not touch
  `android.net.Uri`, `android.content.Context` or any Android class.
  - `core/url/UrlNormalizer` and the matching core of `engine/AdBlockEngine`
    (`matchesHostAndPath`, `isTrackingParameter`, `sanitizeFileName`) are pure on purpose.
  - `AdBlockEngine.cleanUrl` is the deliberate exception — it needs a real parser, so it is
    exercised on-device only. Don't add Robolectric just to cover it; call it out instead.
- `ViewModel`s are unit-testable but currently aren't tested: they need Hilt injection and
  `viewModelScope`, so `kotlinx-coroutines-test` is on the classpath for when someone does it.
- The adblock engine is **not** JVM-testable and is not covered by `AdBlockEngineTest`. Its behaviour
  is covered by the Rust tests in `native/helios-adblock/src/lib.rs` (`cargo test`), which is the only
  place `$third-party` scoping, `@@` exceptions, `$domain=`, cosmetic selectors, generic hiding and
  the serialise round trip are exercised. Do not try to cover them from Kotlin.

## Architecture

Hilt + Room + DataStore + MVI. Package layout:

```
HeliosApplication.kt        @HiltAndroidApp, enables Safe Browsing once per process
MainActivity.kt             @AndroidEntryPoint, gates setup vs. browser
core/url/UrlNormalizer      pure URL parsing: scheme-less input, HTTPS upgrade, host extraction
data/local/                 Room: HeliosDatabase, BookmarkDao, HistoryDao
data/repository/            SettingsRepository (DataStore), BookmarkRepository, HistoryRepository
di/AppModule.kt             Room + DAOs, @IoDispatcher / @DefaultDispatcher qualifiers
domain/model/               AppSettings, BlockingConfig, BrowserTab, SearchEngine,
                            Bookmark, HistoryEntry, DefaultShortcuts
engine/                     AdBlockEngine (entry point), NativeAdBlock (JNI), BlockListRepository,
                            BlockingEngineStatus, WebRequestClassifier, CosmeticFilterEngine,
                            HeliosWebViewClient, HeliosChromeClient, DownloadDispatcher,
                            ExternalUrlLauncher, WebViewFactory, WebPermissionHost
native/helios-adblock/      Rust crate: JNI shim over brave/adblock-rust (MPL-2.0)
ui/adaptive/                HeliosLayout (breakpoints) + rememberHeliosLayout
ui/browser/                 BrowserContract (State/Intent/Effect), BrowserViewModel, BrowserScreen
ui/onboarding/              OnboardingContract, OnboardingViewModel, OnboardingScreen
ui/components/              AddressBar, StartPageView, TabSwitcher, ShieldsSheet,
                            BrowserMenuSheet, BookmarksSheet, HistorySheet, GlassmorphicSurface
ui/splash/                  SplashScreen + HeliosSunMark / HeliosOrbitRing / rememberSteadySpin
ui/icons/, ui/theme/        hand-drawn vectors, Helios* colours, bundled-font type scale
```

### The adblock engine — read this before touching blocking

- **The native engine is authoritative.** `AdBlockEngine.shouldBlock` delegates to `NativeAdBlock`
  whenever an engine is loaded, and a "do not block" from the engine is final. The compiled-in
  `blockedDomains` set is a **fallback for the first few seconds of a cold start only** — running it
  alongside a loaded engine would over-block, because it cannot tell a site's own tracker from a
  third party's.
- **`sourceUrl` is not optional.** `$third-party` and `$domain=` rules are resolved against the
  top-level document. Without it the engine must assume every request is third-party, which takes
  sites off their own CDNs. `HeliosWebViewClient` passes `view?.url` for exactly this reason.
- **The resource type is inferred, not given.** Android's `WebResourceRequest` has no content type,
  unlike Gecko/Blink. `WebRequestClassifier` guesses from the extension and the `Accept` header.
  It is a heuristic; `WebRequestClassifierTest` pins its behaviour.
- **Every JNI entry point fails open.** `catch_unwind` + a fallback value. A bug in the blocker
  degrades to "ads get through"; a SIGABRT is not an acceptable alternative. Do not remove the
  `fail_open!` wrappers.
- **The engine is cached to disk** (`filesDir/blocklist/engine.bin`). The serialised form is
  checksummed and version-checked, so a rejected cache costs one wasted parse, never a wrong engine.
- **The native library is optional at runtime.** No `.so` → `NativeAdBlock.isAvailable` is false →
  the small host list is used and the Shields sheet says so in words. Never let a missing library
  become a crash.
- **Licence:** `brave/adblock-rust` is MPL-2.0. The file-level copyleft applies to
  `native/helios-adblock/**`; those files must stay MPL-2.0. The Kotlin side talks to it across a
  JNI boundary and is not a derivative work.

### The MVI loop — read this before adding UI

- **State** is one `StateFlow<BrowserState>` in `BrowserViewModel`. **`BrowserTab` has only `val`
  fields on purpose.** The old `TabModel` used `var` on a non-observable data class, so mutating
  `tab.progress` did not recompose anything. The fix is copy-on-write: every mutation produces a new
  tab inside a new list, so `StateFlow` sees a different instance. Do not reintroduce `var`.
- **Intents** go through `onIntent()`. It is safe to call from any thread — WebView callbacks
  arrive off the main thread — and compound tab updates take `lock`.
- **Effects** (`BrowserEffect`) are one-shot commands pushed through a `Channel` because they need
  an Activity or a WebView: load/reload/goBack, share sheet, clipboard, external app launch,
  download permission prompt, snackbars.
- `BrowserViewModel` must **never** hold a `WebView`, `Uri`, `PermissionRequest` or
  `GeolocationPermissions.Callback`. Platform plumbing lives in `BrowserScreen`, behind the
  `engine/WebPermissionHost` interface. That seam is the whole reason the VM stays testable.
- `blockingConfig` is a `@Volatile` val, not a Flow, because `shouldInterceptRequest` runs on a
  WebView thread and cannot suspend. `observeSettings()` is the only writer.
- `onCleared()` flushes the session through a private `flushScope`, **not** `viewModelScope` —
  `viewModelScope` is already cancelled by the time `onCleared` runs, so a launch there silently
  does nothing.

### Storage split (deliberate)

- Room for bookmarks and history: relational, queried, ordered, capped (`HistoryRepository.MAX_ENTRIES`).
- DataStore for `AppSettings` plus a small JSON session snapshot of tabs. A session is a handful of
  rows read and written as one blob, so a DAO would be ceremony. Incognito tabs are filtered out
  before the snapshot is written.
- `SettingsRepository.settings` is the **single source of truth** for settings. `BrowserViewModel`
  and `OnboardingViewModel` both observe it, so finishing setup needs no handoff plumbing.

### Browser invariants

- `"helios://start"` (`BrowserTab.START_PAGE_URL`) means "no page loaded". Never `loadUrl` it into
  a real WebView. Default `BrowserTab` title lives in `BrowserTab.DEFAULT_TITLE`.
- WebViews are built only by `engine/WebViewFactory` and cached in `BrowserScreen`'s `webViews` map
  keyed by tab id, because `AndroidView` throws away its content when it leaves the composition —
  that would otherwise discard scroll position on every overlay change. Compose never disposes
  them: `BrowserScreen` destroys them explicitly for tabs that leave `state.tabs`.
- Non-http(s) links (`mailto:`, `tel:`, `intent:`, `market:`, app schemes) must go out as
  `BrowserIntent.ExternalNavigate` -> `BrowserEffect.LaunchExternalApp`. Routing them through
  `Navigate` would hand a `mailto:` URL to `webView.loadUrl` and fail silently. The manifest
  `<queries>` block exists so `resolveActivity` can still see handlers on API 30+.
- `usesCleartextTraffic="false"` and `upgradeToHttps` default on. `MIXED_CONTENT_NEVER_ALLOW`.
- `WebPermissionHost` grants nothing on its own: it forwards every Chromium request to the host and
  always answers a pending request exactly once. DRM is denied outright (no Widevine CDM shipped).
- Downloads: API 29+ writes to the public Downloads collection with no permission; API 26-28 asks
  for `WRITE_EXTERNAL_STORAGE` first and falls back to the app-scoped external files dir. The
  retry path goes `BrowserEffect.DownloadPermissionRequired` -> `BrowserIntent.DownloadPermissionResult`.
- Incognito is best-effort isolation: cookies and third-party cookies disabled, no history, no
  bookmarks, not in the session snapshot. Real isolation would need a separate WebView data
  directory, which means a separate Android process. Don't describe it as more than that.
- Tab switcher cards are static — no WebView snapshotting. Don't claim live previews.

## Conventions

- **Zero emoji in the UI** (explicit project rule). All iconography is hand-written `ImageVector`,
  24dp viewport, paths filled `SolidColor(Color.White)` and tinted at the `Icon(...)` call site.
  Existing vectors: `HeliosIcons` (shield, tabs, lock, refresh, plus, close, arrows, more, search,
  sparkle, incognito, desktop, trash) and `HeliosGlyphs` in `HeliosIconsExtra.kt` (bookmark,
  bookmark-filled, history, download, check, globe, radiance). Add new symbols there, not inline.
- Colours are top-level `val`s in `ui/theme/Color.kt` (`Helios*`). Reuse them instead of inline
  literals. Dark/OLED-only (`HeliosOledBackground` = pure black).
- Glass surfaces go through `GlassmorphicSurface`.
- UI copy is hardcoded English literals inside composables. `res/values/strings.xml` is largely
  unused — follow the existing literal style unless the task is explicitly about localization.
- Material3 `ModalBottomSheet` needs `@OptIn(ExperimentalMaterial3Api::class)` (`ShieldsSheet`,
  `BrowserMenuSheet`, `BookmarksSheet`, `HistorySheet`).
- Sheet scaffolding helpers `SheetHandle()`, `SheetTitle()`, `EmptyState()` live in
  `BookmarksSheet.kt` as `internal` and are shared by `HistorySheet.kt` — same package, no import.
- Manifest quirks to respect: `configChanges` covers orientation/screenSize/screenLayout/
  keyboardHidden (activity is not recreated on rotation), and the http/https `VIEW` intent filters
  live on the same activity as `LAUNCHER`.

## Verified working vs. not

- Implemented and wired to real state: ad/tracker blocking, cosmetic filters, tracking-param
  cleaning, HTTPS upgrade, bookmarks, history, session restore, settings persistence, first-run
  setup, downloads, file chooser, camera/mic/geolocation prompts, external app links, share,
  clipboard, per-tab desktop mode, real incognito tab behaviour.
- Implemented and wired to real state: ad/tracker blocking via adblock-rust (EasyList + EasyPrivacy,
  bundled and refreshable), three-layer cosmetic filtering, tracking-param cleaning, HTTPS upgrade,
  bookmarks, history, session restore, settings persistence, five-page first-run setup, downloads,
  file chooser, camera/mic/geolocation prompts, external app links, share, clipboard, per-tab desktop
  mode, real incognito tab behaviour, animated splash, animated launcher icon, bundled fonts,
  adaptive layout with a navigation rail.
- Known gaps: no home-screen shortcuts (`BrowserEffect.CreateShortcut` shows a "not supported yet"
  message); no tab thumbnails; no bookmark import/export; `AdBlockEngine.cleanUrl` has no unit test;
  the resource-type inference in `WebRequestClassifier` is a heuristic and is wrong sometimes;
  generic hiding costs one `evaluateJavascript` round trip per navigation on pages that need it;
  animated launcher icons only animate on launchers that support them.
