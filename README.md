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

* **iOS-Inspired Floating Omnibox:**
  * Anchored to the bottom for effortless one-handed thumb navigation.
  * Translucent frosted glass effect (`RenderEffect` blur and specular border highlights).
  * Auto-collapses dynamically during page scrolling to maximize viewing space.
  * **Swipe-to-Switch Tabs:** Swipe horizontally left or right on the address bar to switch between open tabs without entering the tab grid.

* **Helios Shields (Ad & Tracker Blocker):**
  * **Network-Level Interception:** Kills outgoing calls to over 150 top ad exchanges, analytics trackers, and telemetry domains before bytes are transferred.
  * **Cosmetic Element Filtering:** Dynamically injects CSS style rules on page commits to eliminate empty banner frames and placeholder spaces.
  * **URL Privacy Sanitizer:** Automatically strips tracking parameters (`utm_source`, `utm_medium`, `fbclid`, `gclid`, etc.) when following hyperlinks.
  * **Live Shields Dashboard:** Instant inspection of blocked trackers, data saved, and site-level privacy toggles.

* **Card Deck Tab Switcher:**
  * Two-column grid with live previews, spring-physics animations, and quick close triggers.
  * **Stealth Private Browsing:** Isolated incognito tab mode with zero cookie, cache, or history retention.

* **Zero Emojis - Custom Vector Symbols:**
  * Clean vector iconography across the browser: Shields, Tabs, Locks, Compass, Radiance, Desktop, and Incognito symbols.

---

### Architecture & Tech Stack

* **Language:** 100% Modern Kotlin
* **UI Framework:** Jetpack Compose + Material 3 + AndroidX Foundation
* **Engine:** AndroidX WebKit with hardware-accelerated Chromium backend
* **Concurrency:** Kotlin Coroutines & Flow
* **CI/CD:** Automated builds via GitHub Actions (`.github/workflows/build.yml`) on Ubuntu runners compiling APK artifacts on every commit.

---

### Building the Project

The easiest way to build Helios is via GitHub Actions:

1. Push this repository to GitHub.
2. The GitHub Actions workflow automatically triggers on `push`.
3. Go to the **Actions** tab on your GitHub repository.
4. Download the compiled `Helios-Browser-Beta` artifact containing the installable APK.

#### Manual / Local Build:
```bash
./gradlew assembleDebug
```
The output APK will be generated at `app/build/outputs/apk/debug/app-debug.apk`.

---

### License

Distributed under the MIT License.
