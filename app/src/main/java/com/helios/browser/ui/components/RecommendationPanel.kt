package com.helios.browser.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helios.browser.domain.model.AppSettings
import com.helios.browser.ui.browser.BrowserIntent
import com.helios.browser.ui.browser.BrowserOverlay
import com.helios.browser.ui.icons.HeliosGlyphs
import com.helios.browser.ui.icons.HeliosIcons
import com.helios.browser.ui.theme.HeliosDarkCard
import com.helios.browser.ui.theme.HeliosShieldGreen
import com.helios.browser.ui.theme.HeliosSun
import com.helios.browser.ui.theme.HeliosTextPrimary
import com.helios.browser.ui.theme.HeliosTextSecondary
import com.helios.browser.ui.theme.HeliosTextTertiary

/**
 * One piece of advice.
 *
 * @param action label for the button that applies it, or null when there is nothing to apply.
 * @param intent the change the action proposes.
 * @param recommended true for something to do, false for a known limitation. The two are rendered in
 *   separate sections because a user who has read the limitations should not have to read them again
 *   among the tips.
 */
private class Tip(
    val title: String,
    val detail: String,
    val action: String?,
    val intent: BrowserIntent?,
    val recommended: Boolean
)

/**
 * Advice about how to get the most out of Helios, including what it cannot do.
 *
 * ## Why this includes the limitations
 * A recommendations panel that only lists things Helios does is marketing, and there is no way for
 * the user to tell the difference until they hit the gap. So the "Not yet" section is a first-class
 * part of this, not an afterthought, and every item in it is something that genuinely is missing.
 *
 * ## Why the tips are derived, not hardcoded
 * Every actionable tip is conditional on a setting, so it disappears once it no longer applies. A
 * recommendation you have already followed should not still be nagging you.
 */
@Composable
fun RecommendationPanel(
    settings: AppSettings,
    onIntent: (BrowserIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    val tips = remember(settings) { buildTips(settings) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(HeliosDarkCard)
            .padding(14.dp)
    ) {
        TipSection(
            heading = "Worth turning on",
            emptyText = "Everything worth turning on is already on.",
            items = tips.filter { it.recommended },
            onIntent = onIntent
        )

        TipSection(
            heading = "Not in Helios yet",
            emptyText = "",
            items = tips.filterNot { it.recommended },
            onIntent = onIntent
        )
    }
}

/**
 * Builds the tips for the current settings.
 *
 * Ordered by how much privacy is at stake, not alphabetically: a list sorted by name would put
 * "Cosmetic filtering is off" above "ads and trackers are being allowed through".
 */
private fun buildTips(settings: AppSettings): List<Tip> = buildList {
    if (!settings.blockAdsAndTrackers) {
        add(
            Tip(
                title = "Blocking is off",
                detail = "Ads and trackers are being allowed through. This is the single biggest " +
                    "thing Helios does, so it is worth turning back on.",
                action = "Open Shields",
                intent = BrowserIntent.ShowOverlay(BrowserOverlay.Shields),
                recommended = true
            )
        )
    }
    if (!settings.safeBrowsingEnabled) {
        add(
            Tip(
                title = "Safe Browsing is off",
                detail = "Without it, malware and phishing pages get no warning. Helios enables it " +
                    "per WebView, so it costs nothing until you actually browse.",
                action = "Turn it on",
                intent = BrowserIntent.SetSafeBrowsing(true),
                recommended = true
            )
        )
    }
    if (!settings.cleanTrackingParams) {
        add(
            Tip(
                title = "Tracking parameters are not being stripped",
                detail = "Turning this on removes utm_*, fbclid and gclid from links you follow, so " +
                    "the site that sent you there never learns which link you clicked.",
                action = "Turn it on",
                intent = BrowserIntent.SetCleanUrls(true),
                recommended = true
            )
        )
    }
    if (!settings.cosmeticFilters) {
        add(
            Tip(
                title = "Cosmetic filtering is off",
                detail = "Network blocking stops a tracker loading at all. This is what hides the " +
                    "empty banner it left behind, so the two work differently.",
                action = "Turn it on",
                intent = BrowserIntent.SetCosmeticFilters(true),
                recommended = true
            )
        )
    }

    add(
        Tip(
            title = "Private tabs are not isolated",
            detail = "They block cookies and stay out of history, bookmarks and the session, but " +
                "they share the process cookie jar with normal tabs. Real isolation needs a " +
                "separate Android process, which Helios does not do.",
            action = null,
            intent = null,
            recommended = false
        )
    )
    add(
        Tip(
            title = "Tab cards are not live previews",
            detail = "The switcher draws a card per tab rather than a screenshot of the page. " +
                "Snapshotting every tab would hold a bitmap for each one, which is the wrong trade " +
                "on a phone with little memory.",
            action = null,
            intent = null,
            recommended = false
        )
    )
    add(
        Tip(
            title = "No bookmark import or export",
            detail = "Bookmarks live in a database on this device. There is no file format to move " +
                "them in or out of yet.",
            action = null,
            intent = null,
            recommended = false
        )
    )
    add(
        Tip(
            title = "No home-screen shortcuts",
            detail = "Helios cannot yet put a shortcut to the current page on your home screen.",
            action = null,
            intent = null,
            recommended = false
        )
    )
    if (!settings.saveHistoryEnabled) {
        add(
            Tip(
                title = "History is off",
                detail = "Nothing is remembered between sessions. Worth leaving on if you clear " +
                    "history manually anyway.",
                action = null,
                intent = null,
                recommended = false
            )
        )
    }
}

@Composable
private fun TipSection(
    heading: String,
    emptyText: String,
    items: List<Tip>,
    onIntent: (BrowserIntent) -> Unit
) {
    Column {
        Text(
            text = heading,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = HeliosTextSecondary
        )
        Spacer(modifier = Modifier.height(6.dp))
        if (items.isEmpty()) {
            if (emptyText.isNotBlank()) {
                Text(
                    text = emptyText,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = HeliosTextTertiary
                )
            }
            return@Column
        }
        items.forEach { tip -> TipCard(tip = tip, onIntent = onIntent) }
    }
}

@Composable
private fun TipCard(tip: Tip, onIntent: (BrowserIntent) -> Unit) {
    val actionable = tip.intent != null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .then(
                if (actionable) {
                    Modifier.clickable { tip.intent?.let(onIntent) }
                } else {
                    Modifier
                }
            )
            .padding(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = if (tip.recommended) HeliosIcons.Sparkle else HeliosGlyphs.Info,
            contentDescription = null,
            tint = if (tip.recommended) HeliosSun else HeliosTextTertiary,
            modifier = Modifier
                .padding(top = 1.dp)
                .size(15.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = tip.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (tip.recommended) HeliosTextPrimary else HeliosTextSecondary
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = tip.detail,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = HeliosTextTertiary
            )
            if (tip.action != null) {
                Spacer(modifier = Modifier.height(7.dp))
                Text(
                    text = tip.action,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = HeliosSun
                )
            }
        }
    }
}

/**
 * A compact "why Helios" strip.
 *
 * Separate from [RecommendationPanel] because this one is static, belongs on the start page, and
 * answers a different question: not "what should I change" but "what is this thing".
 */
@Composable
fun WhyHeliosStrip(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(HeliosDarkCard)
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Icon(
            imageVector = HeliosIcons.Shield,
            contentDescription = null,
            tint = HeliosShieldGreen,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Brave's own blocking engine",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = HeliosTextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "The same adblock-rust that ships in Brave, with EasyList and EasyPrivacy " +
                    "bundled in so it works with no network at all.",
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = HeliosTextSecondary
            )
        }
    }
}