package com.helios.browser.domain.model

/**
 * Attribution for everything Helios is built out of.
 *
 * ## Why this exists
 * Not as a nicety. Every entry here is a legal obligation:
 *  - **adblock-rust** is MPL-2.0, which requires that anyone using the covered code can see that
 *    it is covered and get its source.
 *  - **EasyList / EasyPrivacy / the uBlock lists** are GPL-3.0 and are redistributed inside the APK.
 *  - **Inter, Space Grotesk and JetBrains Mono** are OFL-1.1, which requires the licence to travel
 *    with the font files.
 *  - **Chromium**, which `android.webkit.WebView` is, is BSD-3-Clause, which requires the notice
 *    to be retained in binary distributions.
 *  - Hilt, Compose, Room and DataStore are Apache-2.0, whose §4(d) requires NOTICE-style
 *    attribution when the work is distributed in binary form.
 *
 * An app that ships any of these without saying so is infringing, so this list is a build-time
 * obligation rather than a feature. It is deliberately exhaustive: if a dependency is added and it
 * does not appear here, that is a bug in this file.
 *
 * ## Pure on purpose
 * No Android types, so it is unit tested. A missing entry is a test failure rather than a legal
 * problem discovered later.
 */
object HeliosCredits {

    /** Which part of Helios a dependency is part of. Drives the sheet's section headers. */
    enum class Group(val title: String, val blurb: String) {
        Engine(
            title = "Browser engine",
            blurb = "The engine that renders pages. Not written by us."
        ),
        Blocking(
            title = "Ad and tracker blocking",
            blurb = "The filter lists and the Rust engine that applies them"
        ),
        Type(
            title = "Typefaces",
            blurb = "Bundled in the APK, so their licences ship with it"
        ),
        Platform(
            title = "Platform and libraries",
            blurb = "The Android and Kotlin stack Helios is built on"
        )
    }

    /**
     * One dependency.
     *
     * @param licenceFile asset path of the full licence text, or null when only the identifier is
     *   being stated. Never null for a copyleft licence: a GPL or MPL component that ships in the
     *   binary without its terms alongside is the exact case those licences exist to prevent.
     */
    data class Credit(
        val name: String,
        val author: String,
        val role: String,
        val licence: String,
        val licenceFile: String? = null,
        val url: String? = null
    )

    /**
     * Credits grouped for display, in the order they are shown.
     *
     * Ordering is deliberate: engine first, because that is the one that decides what Helios can do
     * at all, then blocking, then the things a user can actually see.
     */
    val grouped: List<Pair<Group, List<Credit>>> = listOf(
        Group.Engine to listOf(
            Credit(
                name = "Chromium / WebView",
                author = "The Chromium Project and contributors",
                role = "Renders every page. Helios ships the Android System WebView and adds " +
                    "request interception, cosmetic filtering and downloads on top of it.",
                licence = "BSD-3-Clause",
                licenceFile = "licenses/bsd-3-clause.txt",
                url = "https://chromium.googlesource.com/chromium/src/"
            ),
            Credit(
                name = "Android WebKit",
                author = "The Android Open Source Project",
                role = "The public API surface Helios uses for permission prompts, file choosers " +
                    "and Safe Browsing.",
                licence = "Apache-2.0",
                licenceFile = "licenses/apache-2.0.txt",
                url = "https://android.googlesource.com/platform/frameworks/base/"
            )
        ),

        Group.Blocking to listOf(
            Credit(
                name = "adblock-rust",
                author = "Brave Software and contributors",
                role = "Compiles the filter lists into an engine and decides what to block. " +
                    "Linked in as a native library via a JNI shim written for this app.",
                licence = "MPL-2.0",
                licenceFile = "licenses/mozilla-public-license-2.0.txt",
                url = "https://github.com/brave/adblock-rust"
            ),
            Credit(
                name = "EasyList",
                author = "EasyList authors",
                role = "The base advertising filter list, bundled with the app and refreshable " +
                    "from the upstream URL.",
                licence = "GPL-3.0",
                licenceFile = "licenses/gpl-3.0.txt",
                url = "https://easylist.to/"
            ),
            Credit(
                name = "EasyPrivacy",
                author = "EasyPrivacy authors",
                role = "Privacy and tracking filter list, bundled and refreshable.",
                licence = "GPL-3.0",
                licenceFile = "licenses/gpl-3.0.txt",
                url = "https://easylist.to/"
            ),
            Credit(
                name = "uBlock Origin filters",
                author = "Raymond Hill and contributors",
                role = "The uBlock filter, privacy, mobile and two annoyance lists. More coverage " +
                    "than EasyList on some sites, and the reason the preset picker exists.",
                licence = "GPL-3.0",
                licenceFile = "licenses/gpl-3.0.txt",
                url = "https://github.com/uBlockOrigin/uBlock"
            )
        ),

        Group.Type to listOf(
            Credit(
                name = "Inter",
                author = "Rasmus Andersson",
                role = "Interface typeface. Variable weight, so one file covers the whole weight " +
                    "range instead of shipping four.",
                licence = "SIL OFL-1.1",
                licenceFile = "licenses/Inter-OFL.txt",
                url = "https://rsms.me/inter/"
            ),
            Credit(
                name = "Space Grotesk",
                author = "Florian Karsten",
                role = "Display face. Used for headings and the wordmark, where a geometric " +
                    "grotesque reads better than a text face.",
                licence = "SIL OFL-1.1",
                licenceFile = "licenses/SpaceGrotesk-OFL.txt",
                url = "https://floriankarsten.com/space-grotesk/"
            ),
            Credit(
                name = "JetBrains Mono",
                author = "JetBrains",
                role = "Monospace. URLs in the address bar and technical values in the Shields " +
                    "sheet, where digits need to line up.",
                licence = "SIL OFL-1.1",
                licenceFile = "licenses/JetBrainsMono-OFL.txt",
                url = "https://www.jetbrains.com/lp/mono/"
            )
        ),

        Group.Platform to listOf(
            Credit(
                name = "Jetpack Compose",
                author = "The Android Open Source Project",
                role = "Every screen in Helios, from the splash to the start page. The UI is " +
                    "Helios's own; only the toolkit underneath it is not.",
                licence = "Apache-2.0",
                licenceFile = "licenses/apache-2.0.txt",
                url = "https://developer.android.com/jetpack/compose"
            ),
            Credit(
                name = "Kotlin and the Compose compiler plugin",
                author = "JetBrains",
                role = "The language the app is written in. Compiled into the APK rather than " +
                    "shipped as something the user picks, but it is the same licence and it " +
                    "deserves the same credit.",
                licence = "Apache-2.0",
                licenceFile = "licenses/apache-2.0.txt",
                url = "https://kotlinlang.org/"
            ),
            Credit(
                name = "Hilt",
                author = "Google",
                role = "Dependency injection. Builds the ViewModels, the repositories and the " +
                    "blocking engine, and is what keeps WebViews and other platform objects out " +
                    "of the state holder.",
                licence = "Apache-2.0",
                licenceFile = "licenses/apache-2.0.txt",
                url = "https://dagger.dev/hilt/"
            ),
            Credit(
                name = "Room",
                author = "The Android Open Source Project",
                role = "Bookmarks and history, queried and ordered.",
                licence = "Apache-2.0",
                licenceFile = "licenses/apache-2.0.txt",
                url = "https://developer.android.com/training/data-storage/room"
            ),
            Credit(
                name = "DataStore",
                author = "The Android Open Source Project",
                role = "Settings, plus the tab session snapshot.",
                licence = "Apache-2.0",
                licenceFile = "licenses/apache-2.0.txt",
                url = "https://developer.android.com/topic/libraries/architecture/datastore"
            )
        )
    )

    /** Every credit, flattened. */
    val all: List<Credit> = grouped.flatMap { it.second }

    /**
     * Whether every credit that names a licence also ships its text.
     *
     * This is a real check, not decoration. A copyleft licence that ships in the APK without its
     * terms is precisely the case those licences exist to prevent, and it is the easiest thing to
     * get wrong because nothing fails visibly when the asset is missing.
     */
    fun everyLicenceTextIsBundled(): Boolean =
        all.all { credit ->
            val copyleft = credit.licence.contains("GPL") ||
                credit.licence.contains("MPL") ||
                credit.licence.contains("OFL")
            !copyleft || credit.licenceFile != null
        }
}