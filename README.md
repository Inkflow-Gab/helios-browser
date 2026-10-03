# Helios Browser

> Private, fluid, and beautifully minimalist web browser for Android, crafted with iOS-inspired aesthetics and Brave-grade ad blocking.

---

### About the Project

Helios is an open-source mobile web browser developed entirely using an Android smartphone, Termux, and GitHub Actions CI/CD.

```
Hello, welcome to Helios Browser.
This is just beta so don't expect too much — proudly made by a 17 y/o using only a phone and GitHub.
```

---

### Key Features

* **First-Run Setup:**
  * A five-step welcome flow on first launch: intro, privacy defaults, a permissions explainer, search engine and a review screen.
  * Choose between DuckDuckGo, Brave, Google and Startpage.
  * Pick where the omnibox lives — top or bottom — and the layout follows.
  * Every choice is a starting value, not a lock-in — all of it stays editable in the Shields sheet and the menu.
  * Runs once; the completion flag lives in DataStore so it survives process death.

* **Boot & Brand:**
  * An animated splash: the Helios sun mark spins up, the wordmark settles, and the browser is ready.
  * The launcher icon is an `animated-vector` — the rays rotate on launchers that support it, with the
    orbit rings kept in the background layer so only the rays move.
  * Three bundled fonts (Inter, Space Grotesk, JetBrains Mono), all OFL 1.1, licences in
    `app/src/main/assets/licenses/`.

* **iOS-Inspired Floating Omnibox:**
  * Anchored to the bottom for effortless one-handed thumb navigation, or the top if you prefer.
  * Translucent frosted glass effect (`RenderEffect` blur and specular border highlights).
  * **Swipe-to-Switch Tabs:** Swipe horizontally left or right on the address bar to move between tabs.
  * Connection state is always visible: a lock for HTTPS, a warning icon otherwise.

* **Helios Shields (Ad & Tracker Blocker):**
  * **Real adblock-rust.** Helios links against [`brave/adblock-rust`](https://github.com/brave/adblock-rust) —
    the same engine Brave ships — via a small JNI shim. That means full Adblock Plus syntax:
    `$third-party`, `$domain=`, `@@` exceptions, `$badfilter`, site-specific `##` cosmetic rules,
    generic hiding and uBlock Origin `##+js()` scriptlets.
  * **EasyList + EasyPrivacy**, the same lists Brave and uBlock Origin use by default. They ship
    gzipped in the APK, so blocking works on the first page load with no network at all.
  * **Three-layer cosmetic filtering:** site-specific selectors, scriptlets, and generic hiding that
    asks the page which classes and ids it actually uses.
  * **URL Privacy Sanitizer:** Strips tracking parameters (`utm_*`, `fbclid`, `gclid`, `msclkid`, and 12 more) from links you follow.
  * **HTTPS Upgrade:** Plain `http://` links are rewritten to `https://`, and cleartext is blocked app-wide.
  * **Live Shields Dashboard:** Real counters for requests blocked this session and on the current page,
    the engine's actual state, a one-tap filter-list update, and working toggles for every switch.
  * **Safe Browsing:** Enables the WebView provider's hash database so malware and phishing pages get flagged.

* **Adaptive Layout:**
  * `HeliosLayout.from(widthDp, heightDp, preferTopOmnibox)` is the single breakpoint authority.
  * Grid columns, start-page layout, omnibox position, navigation rail, touch targets and insets all
    derive from it — a phone gets a bottom bar and two columns, a tablet or unfolded foldable gets a
    rail and three or four.

* **Card Deck Tab Switcher:**
  * Responsive card grid with spring-physics animations and quick close triggers.
  * Segmented regular/private toggle.
  * **Stealth Private Browsing:** Incognito tabs disable first- and third-party cookies, and are excluded from history, bookmarks and session restore.

* **Library & Downloads:**
  * Bookmarks and History are real Room-backed databases, not hardcoded lists.
  * History is capped, deduplicated on re-visit, and can be cleared wholesale.
  * Downloads run through `DownloadManager`: straight into the public Downloads folder on Android 10+, with a permission prompt and an app-scoped fallback on Android 8–9.

* **Real WebView Integration:**
  * File upload (`<input type="file">`), camera/microphone requests, and geolocation prompts all request runtime permissions first.
  * DRM playback is denied explicitly because Helios ships no Widevine CDM.
  * `mailto:`, `tel:`, `intent:` and app-scheme links are handed to whichever app claims them, with a clear message when nothing can.
  * Share sheet and copy-link actually work.

* **Zero Emojis - Custom Vector Symbols:**
  * Clean vector iconography across the browser: Shields, Tabs, Locks, Radiance, Desktop, Incognito, Bookmark, History, Globe and more.

---

### Architecture & Tech Stack

* **Language:** 100% Modern Kotlin, plus a small Rust crate for the adblock engine
* **UI Framework:** Jetpack Compose + Material 3 + AndroidX Foundation
* **Engine:** AndroidX WebKit with hardware-accelerated Chromium backend
* **Ad blocking:** `brave/adblock-rust` (Rust, MPL-2.0) behind a JNI shim
* **Architecture:** MVI — `BrowserState` / `BrowserIntent` / `BrowserEffect` with a single `StateFlow` of truth
* **Dependency Injection:** Hilt 2.52 (`@HiltAndroidApp`, `@AndroidEntryPoint`, `@HiltViewModel`)
* **Persistence:** Room (bookmarks, history) + DataStore Preferences (settings, session snapshot)
* **Concurrency:** Kotlin Coroutines & Flow
* **CI/CD:** GitHub Actions on Ubuntu runners builds the native library, runs the Rust tests, then
  compiles the APK on every commit

#### Package layout

```
core/url/       pure URL parsing (JVM-testable: no android.net.Uri)
domain/model/   AppSettings, BlockingConfig, BrowserTab, SearchEngine, Bookmark, HistoryEntry
data/local/     Room database + DAOs
data/repository/ SettingsRepository (DataStore), BookmarkRepository, HistoryRepository
di/             Hilt modules and dispatcher qualifiers
engine/         WebView clients, adblock-rust JNI shim, blocking, downloads, external app links
native/         Rust crate: helios-adblock (JNI over adblock-rust)
ui/adaptive/    HeliosLayout breakpoints + rememberHeliosLayout
ui/browser/     BrowserContract, BrowserViewModel, BrowserScreen
ui/onboarding/  OnboardingContract, OnboardingViewModel, OnboardingScreen
ui/components/  omnibox, start page, tab switcher, sheets
ui/splash/      animated splash + the Helios sun mark
ui/icons/       hand-drawn vector iconography
ui/theme/       colours, typography (bundled fonts)
```

#### Design notes worth knowing

* `BrowserTab` has only `val` fields, and every mutation copies the tab inside a new list. That is
  deliberate: the original `TabModel` used `var` on a non-observable class, so progress and
  bookmark updates silently never redrew. Copy-on-write is what makes the `StateFlow` observable.
* The ViewModel never holds a `WebView`, `Uri` or permission object. Anything needing an Activity
  leaves the ViewModel as a `BrowserEffect` and is executed by the composable.
* **The adblock engine fails open.** Every JNI entry point is wrapped in `catch_unwind` and reports
  "do not block" on any error. A browser that shows an ad is annoying; a browser that dies on
  startup is unusable.
* **The native library is optional at runtime.** A build without the Rust toolchain has no `.so`,
  and `NativeAdBlock.isAvailable` is false. Blocking then falls back to a small compiled-in host
  list, and the Shields sheet says so in words rather than claiming protection it does not have.
* **The engine is cached to disk.** `Engine::serialize` writes a checksummed binary that restores in
  a memory copy instead of a multi-second parse. A rejected cache costs one wasted parse, never a
  wrong engine.
* Tab switcher cards are static. There is no WebView snapshotting for thumbnails.

---

### Building the Project

The easiest way to build Helios is via GitHub Actions:

1. Push this repository to GitHub.
2. The GitHub Actions workflow automatically triggers on `push`.
3. Go to the **Actions** tab on your GitHub repository.
4. Download the compiled `Helios-Browser-Beta` artifact containing the installable APK.

CI runs three jobs:

| Job | What it does |
| --- | --- |
| `adblock-engine` | Installs Rust 1.98.0 + NDK 27.3, runs `cargo ndk` for `arm64-v8a`, `armeabi-v7a` and `x86_64`, and uploads the `.so` files as the `helios-jniLibs` artifact. |
| `rust-tests` | Runs `cargo test` in `native/helios-adblock` — the only tests that exercise the blocking rules. |
| `build` | Downloads the native libraries into `app/src/jniLibs`, verifies they are present, then runs `testDebugUnitTest` and `assembleDebug`. |

The APK build **fails** if any of the three ABIs is missing, so a red engine build blocks the APK
rather than silently shipping one with no ad blocking.

#### Manual / Local Build:
```bash
./gradlew assembleDebug
```
The output APK will be generated at `app/build/outputs/apk/debug/app-debug.apk`. This requires a
local Android SDK (compileSdk 34, build-tools 34.0.0) and JDK 17.

Building the native library by hand:
```bash
cd native/helios-adblock
cargo ndk -t arm64-v8a -t armeabi-v7a -t x86_64 -o ../app/src/jniLibs build --release
```
This needs Rust 1.98.0 and the Android NDK. Without it the app still builds and ships — see the
design note above.

#### Tests
```bash
./gradlew testDebugUnitTest          # Kotlin/JVM tests
cd native/helios-adblock && cargo test --release   # Rust tests
```
Plain JVM JUnit 4 tests, no Robolectric and no emulator. Anything testable is deliberately kept free
of Android framework classes.

---

### License

Helios itself is distributed under the MIT License.

The adblock engine is [`brave/adblock-rust`](https://github.com/brave/adblock-rust), MPL-2.0. Under
MPL-2.0 the file-level copyleft applies to the Rust sources in `native/helios-adblock/`; those files
must remain available under MPL-2.0. The Kotlin side talks to it across a JNI boundary and is not a
derivative work of it.

The bundled fonts are Inter, Space Grotesk and JetBrains Mono, all SIL Open Font License 1.1. The
licence texts are in `app/src/main/assets/licenses/`.

EasyList and EasyPrivacy are © the EasyList authors and are used under their respective licences.
See `app/src/main/assets/licenses/EasyList-LICENSE.txt`.
