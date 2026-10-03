package com.helios.browser.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helios.browser.domain.model.AppSettings
import com.helios.browser.domain.model.BlockingPreset
import com.helios.browser.domain.model.BrowserTab
import com.helios.browser.engine.BlockListSource
import com.helios.browser.ui.browser.BlockingEngineState
import com.helios.browser.ui.browser.BrowserIntent
import com.helios.browser.ui.icons.HeliosGlyphs
import com.helios.browser.ui.theme.HeliosBlue
import com.helios.browser.ui.theme.HeliosDarkCard
import com.helios.browser.ui.theme.HeliosDarkSurface
import com.helios.browser.ui.theme.HeliosOledBackground
import com.helios.browser.ui.theme.HeliosShieldGreen
import com.helios.browser.ui.theme.HeliosSun
import com.helios.browser.ui.theme.HeliosTextPrimary
import com.helios.browser.ui.theme.HeliosTextSecondary
import com.helios.browser.ui.theme.HeliosTextTertiary

/**
 * Shields dashboard and settings.
 *
 * Every switch here is wired to a real persisted setting: turning off ad blocking removes the
 * `shouldInterceptRequest` early-return in `HeliosWebViewClient`, and the other three gate the URL
 * rewrite, the CSS injection and the Safe Browsing client path respectively. There are no local
 * `remember` toggles in this sheet.
 *
 * The [engine] block reports what the adblock engine actually is rather than claiming a capability:
 * if the app was built without the native library, or the lists have not finished parsing, it says
 * so in words, because "Block ads and trackers: on" is not true in either case.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShieldsSheet(
    settings: AppSettings,
    tab: BrowserTab?,
    sessionBlockedCount: Int,
    downloadCount: Int,
    engine: BlockingEngineState,
    onIntent: (BrowserIntent) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = HeliosDarkSurface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.2f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Helios Shields",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = HeliosTextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Everything here applies to all tabs, including private ones.",
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = HeliosTextSecondary
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    value = sessionBlockedCount.toString(),
                    label = "Blocked this session",
                    valueColor = HeliosShieldGreen,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    value = (tab?.blockedCount ?: 0).toString(),
                    label = "Blocked on this page",
                    valueColor = HeliosBlue,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    value = downloadCount.toString(),
                    label = "Downloads started",
                    valueColor = HeliosTextPrimary,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    value = if (tab?.isSecure == true) "Yes" else "No",
                    label = "Connection encrypted",
                    valueColor = if (tab?.isSecure == true) HeliosShieldGreen else HeliosTextSecondary,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            EngineStatusCard(engine = engine, onRefresh = {
                onIntent(BrowserIntent.RefreshBlockLists)
            })

            Spacer(modifier = Modifier.height(16.dp))

            PresetPicker(
                selected = engine.preset,
                active = engine.activePreset,
                rebuilding = engine.isRebuilding,
                enabled = settings.blockAdsAndTrackers,
                onSelect = { onIntent(BrowserIntent.SetBlockingPreset(it)) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            ShieldToggleRow(
                title = "Block ads and trackers",
                subtitle = "Drops requests to known ad and telemetry hosts before transfer",
                checked = settings.blockAdsAndTrackers,
                onCheckedChange = { onIntent(BrowserIntent.SetBlockAds(it)) }
            )

            ShieldToggleRow(
                title = "Hide ad elements",
                subtitle = "Applies the cosmetic rules from the filter lists",
                checked = settings.cosmeticFilters,
                onCheckedChange = { onIntent(BrowserIntent.SetCosmeticFilters(it)) }
            )

            ShieldToggleRow(
                title = "Clean tracking parameters",
                subtitle = "Strips utm, fbclid and gclid from links you follow",
                checked = settings.cleanTrackingParams,
                onCheckedChange = { onIntent(BrowserIntent.SetCleanUrls(it)) }
            )

            ShieldToggleRow(
                title = "Upgrade to HTTPS",
                subtitle = "Rewrites plain HTTP links before loading them",
                checked = settings.upgradeToHttps,
                onCheckedChange = { onIntent(BrowserIntent.SetUpgradeHttps(it)) }
            )

            ShieldToggleRow(
                title = "Safe Browsing",
                subtitle = "Warns you about malware and phishing pages",
                checked = settings.safeBrowsingEnabled,
                onCheckedChange = { onIntent(BrowserIntent.SetSafeBrowsing(it)) }
            )

            ShieldToggleRow(
                title = "Keep history",
                subtitle = "Off means Helios does not remember where you went",
                checked = settings.saveHistoryEnabled,
                onCheckedChange = { onIntent(BrowserIntent.SetSaveHistory(it)) }
            )

            ShieldToggleRow(
                title = "Desktop site mode",
                subtitle = "Request the desktop version of this page",
                checked = tab?.isDesktopMode == true,
                onCheckedChange = { onIntent(BrowserIntent.SetDesktopModeForTab(it)) }
            )
        }
    }
}

/**
 * Chooses which filter lists the engine is built from.
 *
 * Every option states its cost, because the choice is not really "which is better" — it is how much
 * blocking you want against how much memory it costs and how likely a site is to break. Hiding that
 * would make this a list of brand names.
 *
 * The selected row and the row that is actually running are distinguished while a switch is still
 * rebuilding, so the sheet never claims lists are active before they are.
 */
@Composable
private fun PresetPicker(
    selected: BlockingPreset,
    active: BlockingPreset,
    rebuilding: Boolean,
    enabled: Boolean,
    onSelect: (BlockingPreset) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(HeliosDarkCard)
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(20.dp))
            .padding(14.dp)
    ) {
        Text(
            text = "Filter lists",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = HeliosTextPrimary
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = if (enabled) {
                "Which rule sets the engine is built from."
            } else {
                "Blocking is off, so switching lists will not take effect until you turn it back on."
            },
            fontSize = 11.sp,
            lineHeight = 15.sp,
            color = HeliosTextSecondary
        )

        Spacer(modifier = Modifier.height(10.dp))

        BlockingPreset.entries.forEach { preset ->
            val isSelected = preset == selected
            val isActive = preset == active
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (isSelected) HeliosSun.copy(alpha = 0.12f) else Color.Transparent
                    )
                    .clickable { onSelect(preset) }
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) HeliosSun else Color.Transparent)
                        .border(
                            width = 1.5.dp,
                            color = if (isSelected) HeliosSun else HeliosTextTertiary,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = HeliosGlyphs.Check,
                            contentDescription = null,
                            tint = HeliosOledBackground,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = preset.title,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) HeliosSun else HeliosTextPrimary
                        )
                        if (isActive && !isSelected) {
                            // Selected but not yet loaded: the old lists are still what is running.
                            Text(
                                text = "rebuilding",
                                fontSize = 10.sp,
                                color = HeliosTextTertiary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = preset.summary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = HeliosTextSecondary
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = preset.listNames.joinToString(" + ") + " · ${preset.sizeHint}",
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                        color = HeliosTextTertiary
                    )
                }
            }
        }

        if (rebuilding) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Still compiling. Until it finishes, the previous lists are still in force.",
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = HeliosSun
            )
        }
    }
}

/**
 * Reports the adblock engine's real state and offers a refresh.
 *
 * The three states are genuinely different and the user is told which one they are in:
 *  - **no native library** — the build skipped the Rust step, so only a small built-in host list
 *    is in force. Saying "blocking is on" here would be a lie.
 *  - **loading** — the lists are compiling; blocking is using the built-in list meanwhile.
 *  - **ready** — EasyList and EasyPrivacy are loaded, with their provenance shown.
 */
@Composable
private fun EngineStatusCard(
    engine: BlockingEngineState,
    onRefresh: () -> Unit
) {
    val headline = when {
        !engine.isAvailable -> "Adblock engine unavailable"
        engine.isRefreshing -> "Updating filter lists"
        engine.isReady -> "Engine ready"
        else -> "Loading filter lists"
    }
    val detail = when {
        !engine.isAvailable ->
            "This build has no native blocking library. Only the built-in host list is in use, " +
                "which is a small fraction of EasyList."
        engine.isRefreshing -> "Downloading EasyList and EasyPrivacy from easylist.to"
        engine.isReady -> when (engine.source) {
            BlockListSource.CACHE -> "EasyList + EasyPrivacy, restored from the local cache"
            BlockListSource.BUNDLED -> "EasyList + EasyPrivacy, from the copy bundled in the app"
            BlockListSource.REMOTE -> "EasyList + EasyPrivacy, downloaded just now"
            BlockListSource.NONE -> "No filter lists loaded"
        }
        else -> "Compiling the bundled filter lists into the engine"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(HeliosDarkCard)
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(20.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = headline,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = when {
                    !engine.isAvailable -> HeliosTextSecondary
                    engine.isReady -> HeliosShieldGreen
                    else -> HeliosTextPrimary
                }
            )
            if (engine.isAvailable) {
                Text(
                    text = if (engine.isRefreshing) "Updating" else "Update",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (engine.isRefreshing) HeliosTextTertiary else HeliosBlue,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = !engine.isRefreshing) { onRefresh() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = detail,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            color = HeliosTextSecondary
        )
        if (engine.cacheSizeBytes > 0L) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                // Only the cache is reported, not a list size: the engine does not keep a rule count
                // without paying for debug info, and inventing one would be worse than omitting it.
                text = "Compiled engine cached at ${formatBytes(engine.cacheSizeBytes)}",
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = HeliosTextTertiary
            )
        }
    }
}

/** Binary units, matching what a file manager would report for the cache file. */
private fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val units = listOf("KB", "MB", "GB")
    var value = bytes.toDouble() / 1024.0
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }
    return "%.1f %s".format(value, units[unitIndex])
}

@Composable
private fun StatCard(
    value: String,
    label: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(HeliosDarkCard)
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(20.dp))
            .padding(14.dp)
    ) {
        Text(
            text = value,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            color = HeliosTextSecondary
        )
    }
}

@Composable
private fun ShieldToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(HeliosDarkCard)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = HeliosTextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = HeliosTextSecondary
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        // null onCheckedChange keeps the switch non-interactive so the row owns the tap.
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = HeliosShieldGreen,
                uncheckedThumbColor = HeliosTextSecondary,
                uncheckedTrackColor = Color.White.copy(alpha = 0.1f)
            ),
            modifier = Modifier.size(width = 52.dp, height = 32.dp)
        )
    }
}