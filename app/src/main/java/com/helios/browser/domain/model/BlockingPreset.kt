package com.helios.browser.domain.model

/**
 * Which filter lists the adblock engine is built from.
 *
 * ## Why this is a choice at all
 * The lists that ship with Helios are the same ones Brave and uBlock Origin use, but "all of them"
 * is not obviously the right answer for everyone:
 *
 * - The uBlock lists are written by people who actually run uBlock every day, so their network rules
 *   are tighter — and they will occasionally break a site that the lighter lists do not. The
 *   annoyances lists go further and hide cookie banners and site-specific annoyances, which is
 *   exactly the sort of thing that makes a site look broken.
 * - uBlock's own `filters.txt` is a single large list rather than a network/privacy split, so
 *   trading it for EasyList + EasyPrivacy changes what gets blocked as much as how much.
 * - Bigger is not better on a phone. The engine holds every rule in memory, and the preset is a
 *   real memory decision as much as a correctness one.
 *
 * So the honest options are laid out with their costs, and [HELIOS_BALANCED] is the default because
 * it is the pair with the fewest reports against it.
 *
 * ## Changing the preset invalidates the cached engine
 * A serialised engine is the compiled form of one specific set of rules. Restoring an EasyList
 * engine after the user has switched to uBlock would silently apply the old rules, so
 * `BlockListRepository` clears the cache when the preset changes. See `KEY_BLOCKING_PRESET`.
 */
enum class BlockingPreset(
    val title: String,
    val summary: String,
    val lists: List<FilterList>,
    /** Rough engine size, shown so the memory trade-off is not hidden behind a title. */
    val sizeHint: String
) {
    /**
     * EasyList plus EasyPrivacy: ads and trackers, and nothing else.
     *
     * The default because it is the combination with the fewest false-positive reports, and because
     * leaving cookie banners alone is a choice rather than an omission — some sites are genuinely
     * unusable without them.
     */
    HELIOS_BALANCED(
        title = "Helios Balanced",
        summary = "EasyList and EasyPrivacy. Blocks ads and trackers, leaves sites otherwise alone.",
        lists = listOf(FilterList.EASYLIST, FilterList.EASYPRIVACY),
        sizeHint = "About 95,000 rules"
    ),

    /**
     * The uBlock Origin list set, including its annoyances lists.
     *
     * Tighter, and the most likely of these to hide something a site needs. The annoyances lists
     * cover cookie banners and site-specific irritations, which is where "the page looks broken"
     * reports come from.
     */
    UBLOCK_STRICT(
        title = "uBlock Strict",
        summary = "uBlock Origin filters plus its annoyances lists. Tightest, most likely to hide " +
            "something a site needs.",
        lists = listOf(
            FilterList.EASYLIST,
            FilterList.EASYPRIVACY,
            FilterList.UBLOCK_FILTERS,
            FilterList.UBLOCK_PRIVACY,
            FilterList.UBLOCK_ANNOYANCES_COOKIES,
            FilterList.UBLOCK_ANNOYANCES_OTHERS
        ),
        sizeHint = "Largest, and the most memory"
    ),

    /**
     * Balanced, plus uBlock's mobile list.
     *
     * uBlock publishes a separate mobile list for rules that only matter on a phone — mobile ad
     * formats, app-redirect interstitials. Helios is only ever a phone browser, so those rules apply
     * and the desktop-only ones in the main list mostly do not.
     */
    HELIOS_MOBILE(
        title = "Helios Mobile",
        summary = "EasyList, EasyPrivacy and uBlock's mobile list. Tuned for a phone screen.",
        lists = listOf(
            FilterList.EASYLIST,
            FilterList.EASYPRIVACY,
            FilterList.UBLOCK_MOBILE
        ),
        sizeHint = "About 100,000 rules"
    ),

    /**
     * Trackers only, no ad blocking.
     *
     * The smallest option, and the least useful for ad-heavy pages — but it is the one that will not
     * break a site over a cosmetic rule, which is a real failure mode worth having an escape hatch
     * for.
     */
    TRACKERS_ONLY(
        title = "Trackers only",
        summary = "EasyPrivacy and uBlock privacy. No ad blocking, so nothing is hidden by rule.",
        lists = listOf(FilterList.EASYPRIVACY, FilterList.UBLOCK_PRIVACY),
        sizeHint = "Smallest"
    );

    /** Human-readable source names, for the shields sheet. */
    val listNames: List<String>
        get() = lists.map { it.displayName }

    companion object {
        val DEFAULT = HELIOS_BALANCED

        /** Parses a stored name, falling back to [DEFAULT] rather than throwing. */
        fun fromName(name: String?): BlockingPreset =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/**
 * One filter list, identified by its bundled asset and its remote source.
 *
 * The asset is what ships in the APK; the URL is what a refresh pulls. Both are needed because the
 * two must agree — a refresh that changed which list a slot means would silently swap a user's
 * rules, and that is not something to infer from a URL.
 */
enum class FilterList(
    val displayName: String,
    val assetPath: String,
    val remoteUrl: String,
    val licenceNote: String
) {
    EASYLIST(
        displayName = "EasyList",
        assetPath = "blocklists/easylist.txt.gz",
        remoteUrl = "https://easylist.to/easylist/easylist.txt",
        licenceNote = "EasyList authors, GPLv3 with a commercial-use exception"
    ),
    EASYPRIVACY(
        displayName = "EasyPrivacy",
        assetPath = "blocklists/easyprivacy.txt.gz",
        remoteUrl = "https://easylist.to/easylist/easyprivacy.txt",
        licenceNote = "EasyPrivacy authors, GPLv3 with a commercial-use exception"
    ),
    UBLOCK_FILTERS(
        displayName = "uBlock filters",
        assetPath = "blocklists/ublock_filters.txt.gz",
        remoteUrl = "https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/filters.txt",
        licenceNote = "uBlock Origin, GPLv3"
    ),
    UBLOCK_PRIVACY(
        displayName = "uBlock privacy",
        assetPath = "blocklists/ublock_privacy.txt.gz",
        remoteUrl = "https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/privacy.txt",
        licenceNote = "uBlock Origin, GPLv3"
    ),
    UBLOCK_ANNOYANCES_COOKIES(
        displayName = "uBlock cookie banners",
        assetPath = "blocklists/ublock_annoyances_cookies.txt.gz",
        remoteUrl = "https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/annoyances-cookies.txt",
        licenceNote = "uBlock Origin, GPLv3"
    ),
    UBLOCK_ANNOYANCES_OTHERS(
        displayName = "uBlock annoyances",
        assetPath = "blocklists/ublock_annoyances_others.txt.gz",
        remoteUrl = "https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/annoyances-others.txt",
        licenceNote = "uBlock Origin, GPLv3"
    ),
    UBLOCK_MOBILE(
        displayName = "uBlock mobile",
        assetPath = "blocklists/ublock_mobile.txt.gz",
        remoteUrl = "https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/filters-mobile.txt",
        licenceNote = "uBlock Origin, GPLv3"
    )
}