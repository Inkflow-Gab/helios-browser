package com.helios.browser.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helios.browser.BuildConfig
import com.helios.browser.domain.model.AppSettings
import com.helios.browser.domain.model.BrowserTab
import com.helios.browser.domain.model.SearchEngine
import com.helios.browser.ui.browser.BrowserIntent
import com.helios.browser.ui.browser.BrowserOverlay
import com.helios.browser.ui.icons.HeliosGlyphs
import com.helios.browser.ui.icons.HeliosIcons
import com.helios.browser.ui.theme.HeliosDarkCard
import com.helios.browser.ui.theme.HeliosDarkSurface
import com.helios.browser.ui.theme.HeliosPrivateAccent
import com.helios.browser.ui.theme.HeliosShieldGreen
import com.helios.browser.ui.theme.HeliosSun
import com.helios.browser.ui.theme.HeliosTextPrimary
import com.helios.browser.ui.theme.HeliosTextSecondary
import com.helios.browser.ui.theme.HeliosTextTertiary

/**
 * The main menu.
 *
 * ## What was wrong with the previous one
 * Nine full-width cards in a vertical scroll, all the same visual weight, with the search engine
 * buried underneath them as four more rows. Nothing told the eye what mattered. A menu that looks like
 * a list of everything is a menu that reads as nothing.
 *
 * ## The structure now
 * Three tiers, each visually distinct:
 *  1. **A two-column tile grid** for the actions people reach for. Halves the scroll length and makes
 *     them scannable in one pass.
 *  2. **Single rows** for settings that open somewhere else — search engine, shields, privacy,
 *     desktop mode. Each shows its current value, so the row is a readout and not just a button.
 *  3. **Recommendations**, collapsed by default, because advice should be available without being in
 *     the way.
 *
 * All of it is emitted as intents, so nothing here knows whether a page is loaded.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserMenuSheet(
    tab: BrowserTab?,
    settings: AppSettings,
    blockedCount: Int,
    onIntent: (BrowserIntent) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Bound once so the tile list's lambdas do not need `!!` on a captured nullable, and so the
    // page cannot be swapped between composing the header and firing a tile.
    val page = tab?.takeIf { !it.isStartPage }
    val hasRealPage = page != null
    var showRecommendations by remember { mutableStateOf(false) }
    var showEngines by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = HeliosDarkSurface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.18f))
            )
        }
    ) {
        LazyVerticalGrid(
            // Fixed at two rather than adaptive: the tiles are icon-led and must not reflow into a
            // different shape on a tablet. The sheet's own width is capped by the sheet.
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            item(span = { GridItemSpanMax() }) {
                PageHeader(tab = tab, hasRealPage = hasRealPage)
            }

            // --- Tier 1: the actions people actually reach for -------------------------------
            item(span = { GridItemSpanMax() }) {
                TileGrid(
                    tiles = buildList {
                        add(MenuTile("New tab", HeliosIcons.Plus) {
                            onIntent(BrowserIntent.NewTab)
                        })
                        add(
                            MenuTile(
                                title = "Private",
                                icon = HeliosIcons.Incognito,
                                tint = HeliosPrivateAccent
                            ) { onIntent(BrowserIntent.NewPrivateTab) }
                        )
                        add(MenuTile("Tabs", HeliosIcons.Tabs) {
                            onIntent(BrowserIntent.ShowOverlay(BrowserOverlay.Tabs))
                        })
                        add(MenuTile("Bookmarks", HeliosGlyphs.Bookmark) {
                            onIntent(BrowserIntent.ShowOverlay(BrowserOverlay.Bookmarks))
                        })
                        add(MenuTile("History", HeliosGlyphs.History) {
                            onIntent(BrowserIntent.ShowOverlay(BrowserOverlay.History))
                        })
                        val open = page
                        if (open != null) {
                            add(MenuTile("Copy link", HeliosGlyphs.Globe) {
                                onIntent(BrowserIntent.CopyLink(open.url))
                            })
                            add(MenuTile("Share", HeliosIcons.Sparkle) {
                                // (url, title) — the other way round shares a page's title as its
                                // link, which looks broken in the receiving app.
                                onIntent(BrowserIntent.SharePage(open.url, open.title))
                            })
                            // Page-scoped, so it only appears with a page loaded: there are no
                            // cookies to act on otherwise, and offering it there would open onto
                            // an empty sheet that says "open a page first".
                            add(MenuTile("Cookies", HeliosGlyphs.Cookie) {
                                onIntent(BrowserIntent.ShowOverlay(BrowserOverlay.Cookies))
                            })
                        } else {
                            // With no page loaded there is nothing to copy or share, so those two
                            // slots go to the things that are useful on the start page instead.
                            add(MenuTile("Downloads", HeliosGlyphs.Download) {
                                onIntent(BrowserIntent.ShowOverlay(BrowserOverlay.Downloads))
                            })
                            add(MenuTile("Find", HeliosIcons.Search) {
                                onIntent(BrowserIntent.ShowMessage("Find on page needs a page open"))
                            })
                        }
                    }
                )
            }

            // --- Tier 2: settings, each showing its current value ----------------------------
            item(span = { GridItemSpanMax() }) {
                Spacer(modifier = Modifier.height(6.dp))
                SettingsRow(
                    icon = HeliosIcons.Search,
                    title = "Search engine",
                    value = settings.searchEngine.title
                ) { showEngines = !showEngines }
            }

            if (showEngines) {
                item(span = { GridItemSpanMax() }) {
                    EnginePicker(
                        selected = settings.searchEngine,
                        onSelect = {
                            onIntent(BrowserIntent.SetSearchEngine(it))
                            showEngines = false
                        }
                    )
                }
            }

            item(span = { GridItemSpanMax() }) {
                SettingsRow(
                    icon = HeliosIcons.Shield,
                    title = "Helios Shields",
                    value = when {
                        !settings.blockAdsAndTrackers -> "Off"
                        blockedCount > 0 -> "$blockedCount blocked"
                        else -> "On"
                    },
                    tint = if (settings.blockAdsAndTrackers) HeliosShieldGreen else HeliosTextTertiary
                ) {
                    onIntent(BrowserIntent.ShowOverlay(BrowserOverlay.Shields))
                }
            }

            item(span = { GridItemSpanMax() }) {
                SettingsRow(
                    icon = HeliosIcons.Incognito,
                    title = "Private browsing",
                    value = if (settings.desktopModeByDefault) "Desktop sites" else "On",
                    tint = HeliosPrivateAccent
                ) {
                    onIntent(BrowserIntent.SetPrivateMode(!settings.desktopModeByDefault))
                }
            }

            item(span = { GridItemSpanMax() }) {
                SettingsRow(
                    icon = HeliosIcons.Desktop,
                    title = "Desktop site mode",
                    value = if (page?.isDesktopMode == true) "This tab only" else "Off"
                ) {
                    onIntent(BrowserIntent.SetDesktopModeForTab(!(page?.isDesktopMode ?: false)))
                }
            }

            // --- Credits ----------------------------------------------------------------------
            // Sits between the settings and the recommendations: it is a licence obligation, not
            // advice, but it is not something anyone needs while browsing, so it goes last.
            item(span = { GridItemSpanMax() }) {
                SettingsRow(
                    icon = HeliosGlyphs.Info,
                    title = "Credits and licences",
                    value = BuildConfig.VERSION_NAME
                ) {
                    onIntent(BrowserIntent.ShowOverlay(BrowserOverlay.Credits))
                }
            }

            // --- Tier 3: recommendations, out of the way until asked for ----------------------
            item(span = { GridItemSpanMax() }) {
                Spacer(modifier = Modifier.height(6.dp))
                SettingsRow(
                    icon = HeliosGlyphs.Info,
                    title = "Recommendations",
                    value = if (showRecommendations) "Hide" else "What to use, and why"
                ) { showRecommendations = !showRecommendations }
            }

            if (showRecommendations) {
                item(span = { GridItemSpanMax() }) {
                    RecommendationPanel(
                        settings = settings,
                        onIntent = onIntent
                    )
                }
            }
        }
    }
}

/** Full-width row inside the two-column grid. */
private fun GridItemSpanMax() =
    androidx.compose.foundation.lazy.grid.GridItemSpan(2)

/** A tile: one action, icon-led, so the grid stays scannable. */
private class MenuTile(
    val title: String,
    val icon: ImageVector,
    val tint: Color = HeliosTextPrimary,
    val onClick: () -> Unit
)

/**
 * The tile grid.
 *
 * Laid out manually with [Row]s rather than nested lazy grids, because a lazy grid inside a lazy
 * grid cannot measure unbounded and silently shows nothing. Fourteen tiles is not a list worth
 * virtualising.
 */
@Composable
private fun TileGrid(tiles: List<MenuTile>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tiles.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { tile ->
                    MenuTileButton(
                        tile = tile,
                        modifier = Modifier.weight(1f)
                    )
                }
                // Keeps an odd final row aligned with the grid above it.
                if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MenuTileButton(tile: MenuTile, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(HeliosDarkCard)
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(18.dp))
            .clickable(onClick = tile.onClick)
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = tile.icon,
            contentDescription = null,
            tint = tile.tint,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = tile.title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = HeliosTextPrimary
        )
    }
}

/** A settings row that shows its current value on the right, so it reads as a readout too. */
@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    value: String,
    tint: Color = HeliosTextPrimary,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(HeliosDarkCard)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(19.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = HeliosTextPrimary,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            fontSize = 12.sp,
            color = HeliosTextSecondary,
            maxLines = 1
        )
    }
}

/** Compact radio list. Only visible when expanded, so it costs nothing when closed. */
@Composable
private fun EnginePicker(
    selected: SearchEngine,
    onSelect: (SearchEngine) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(HeliosDarkCard)
            .padding(vertical = 4.dp)
    ) {
        SearchEngine.entries.forEach { engine ->
            val isSelected = engine == selected
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(engine) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) HeliosSun else Color.Transparent)
                        .border(
                            width = 1.5.dp,
                            color = if (isSelected) HeliosSun else HeliosTextTertiary,
                            shape = CircleShape
                        )
                )
                Spacer(modifier = Modifier.width(11.dp))
                Text(
                    text = engine.title,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) HeliosSun else HeliosTextPrimary
                )
            }
        }
    }
}

/** Quiet header: who and where, without competing with the actions. */
@Composable
private fun PageHeader(tab: BrowserTab?, hasRealPage: Boolean) {
    if (!hasRealPage) {
        Text(
            text = "Helios",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = HeliosTextPrimary,
            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
        )
        return
    }
    // Bound to a local so every use below is on a non-null receiver. `tab` is a parameter and the
    // compiler will not smart-cast it from `hasRealPage`, because that flag is not a proof of it.
    val page = tab ?: return
    Column(modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)) {
        Text(
            text = page.title,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = HeliosTextPrimary,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (page.isSecure) HeliosIcons.Lock else HeliosIcons.Sparkle,
                contentDescription = null,
                tint = if (page.isSecure) HeliosShieldGreen else HeliosTextSecondary,
                modifier = Modifier.size(11.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = page.displayHost,
                fontSize = 11.sp,
                color = HeliosTextSecondary,
                maxLines = 1
            )
        }
    }
}