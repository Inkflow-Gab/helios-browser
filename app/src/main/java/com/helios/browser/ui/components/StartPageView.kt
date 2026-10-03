package com.helios.browser.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helios.browser.domain.model.DefaultShortcuts
import com.helios.browser.domain.model.SearchEngine
import com.helios.browser.ui.adaptive.HeliosLayout
import com.helios.browser.ui.browser.BrowserIntent
import com.helios.browser.ui.browser.BrowserOverlay
import com.helios.browser.ui.browser.BrowserState
import com.helios.browser.ui.icons.HeliosGlyphs
import com.helios.browser.ui.icons.HeliosIcons
import com.helios.browser.ui.splash.HeliosOrbitRing
import com.helios.browser.ui.splash.HeliosSunMark
import com.helios.browser.ui.theme.HeliosBlue
import com.helios.browser.ui.theme.HeliosDarkCard
import com.helios.browser.ui.theme.HeliosShieldGreen
import com.helios.browser.ui.theme.HeliosSun
import com.helios.browser.ui.theme.HeliosTextPrimary
import com.helios.browser.ui.theme.HeliosTextSecondary
import com.helios.browser.ui.theme.HeliosTextTertiary

/**
 * The Helios start page: what a tab shows before anything is loaded.
 *
 * ## What it is for
 * A start page in a privacy browser is not decoration. It is the one surface that is always
 * available, always local and always fast, so it carries the three things worth having there: a
 * search box that goes somewhere, a count of what blocking has done, and a way back to your own
 * saved pages. Everything below is real state — the count is the session's, the shortcuts are the
 * ones the user can edit, and the engine picker writes through to settings.
 *
 * ## The search box
 * It is a real input, not a picture of one. Submitting it emits [BrowserIntent.Navigate], which is
 * the same intent the omnibox uses, so the two paths cannot drift apart. The query is handed over
 * raw and the existing URL-detection logic decides whether it is a search or an address.
 *
 * ## Layout
 * The grid follows [HeliosLayout.startPageColumns] so a phone gets two columns and a tablet gets
 * three or four. The bottom reserve clears the omnibox wherever it actually is.
 */
@Composable
fun StartPageView(
    state: BrowserState,
    layout: HeliosLayout,
    onIntent: (BrowserIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    // The bottom padding has to clear the omnibox wherever it actually is, otherwise the last row
    // of shortcuts sits underneath it. On a rail layout the bar is at the top and nothing is hidden,
    // so the reserve can shrink.
    val bottomReserve = if (layout.omniboxAtTop) 32.dp else 108.dp

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 20.dp, bottom = bottomReserve),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item { HeliosWordmark() }

        item {
            StartPageSearchBox(
                engine = state.settings.searchEngine,
                onSubmit = { onIntent(BrowserIntent.Navigate(it)) }
            )
        }

        item {
            BlockingSummary(
                sessionBlockedCount = state.sessionBlockedCount,
                blockingEnabled = state.settings.blockAdsAndTrackers,
                engineReady = state.blockingEngine.isReady,
                engineAvailable = state.blockingEngine.isAvailable,
                onClick = { onIntent(BrowserIntent.ShowOverlay(BrowserOverlay.Shields)) }
            )
        }

        item {
            SectionHeader(
                title = "Search engine",
                onActionClick = { onIntent(BrowserIntent.ShowOverlay(BrowserOverlay.Menu)) },
                actionLabel = "Change"
            )
            Spacer(modifier = Modifier.height(10.dp))
            SearchEngineRow(
                engines = SearchEngine.entries,
                selected = state.settings.searchEngine,
                onSelect = { onIntent(BrowserIntent.SetSearchEngine(it)) }
            )
        }

        item {
            SectionHeader(
                title = "Your pages",
                onActionClick = { onIntent(BrowserIntent.ShowOverlay(BrowserOverlay.Bookmarks)) },
                actionLabel = "Bookmarks"
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickLinkTile(
                    label = "Bookmarks",
                    count = state.bookmarks.size,
                    icon = HeliosGlyphs.Bookmark,
                    onClick = { onIntent(BrowserIntent.ShowOverlay(BrowserOverlay.Bookmarks)) },
                    modifier = Modifier.weight(1f)
                )
                QuickLinkTile(
                    label = "History",
                    count = state.history.size,
                    icon = HeliosGlyphs.History,
                    onClick = { onIntent(BrowserIntent.ShowOverlay(BrowserOverlay.History)) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            SectionHeader(title = "Quick access", onActionClick = null, actionLabel = null)
            Spacer(modifier = Modifier.height(12.dp))
            val columns = layout.startPageColumns.coerceAtLeast(1)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DefaultShortcuts.items.chunked(columns).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        row.forEach { (title, url, initial) ->
                            ShortcutTile(
                                title = title,
                                initial = initial,
                                onClick = { onIntent(BrowserIntent.Navigate(url)) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        // Keep the last row aligned with the grid above it.
                        repeat(columns - row.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

/**
 * The animated sun mark above the wordmark.
 *
 * Reuses the splash's mark rather than drawing a second one, so the icon on the home screen, the
 * splash and the start page are visibly the same thing. The orbit ring is decorative and drawn at
 * low alpha behind it.
 */
@Composable
private fun HeliosWordmark() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center) {
            HeliosOrbitRing(
                modifier = Modifier.size(104.dp),
                color = HeliosSun.copy(alpha = 0.16f)
            )
            HeliosSunMark(modifier = Modifier.size(76.dp))
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Helios",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = HeliosTextPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "A browser that minds its own business",
            fontSize = 12.sp,
            color = HeliosTextTertiary,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * A working search field.
 *
 * Local state only — the query is not persisted, because a half-typed search is not something that
 * belongs in a session snapshot. Submitting clears it, so the box is ready for the next thing.
 */
@Composable
private fun StartPageSearchBox(
    engine: SearchEngine,
    onSubmit: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    TextField(
        value = query,
        onValueChange = { query = it },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(HeliosDarkCard)
            .border(
                width = 1.dp,
                color = if (isFocused) HeliosSun.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(20.dp)
            ),
        placeholder = {
            Text(
                text = "Search ${engine.title} or type an address",
                fontSize = 14.sp,
                color = HeliosTextTertiary
            )
        },
        leadingIcon = {
            Icon(
                imageVector = HeliosIcons.Search,
                contentDescription = null,
                tint = HeliosTextTertiary,
                modifier = Modifier.size(20.dp)
            )
        },
        trailingIcon = {
            if (query.isNotBlank()) {
                Icon(
                    imageVector = HeliosIcons.Close,
                    contentDescription = "Clear",
                    tint = HeliosTextTertiary,
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .clickable { query = "" }
                        .padding(2.dp)
                )
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(
            onSearch = {
                val trimmed = query.trim()
                if (trimmed.isNotEmpty()) {
                    onSubmit(trimmed)
                    query = ""
                    keyboard?.hide()
                }
            }
        ),
        interactionSource = interactionSource,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            focusedTextColor = HeliosTextPrimary,
            unfocusedTextColor = HeliosTextPrimary,
            cursorColor = HeliosSun,
            focusedPlaceholderColor = HeliosTextTertiary,
            unfocusedPlaceholderColor = HeliosTextTertiary
        )
    )
}

/**
 * What blocking has done this session, and whether it is on.
 *
 * The engine's readiness is reported alongside the count because the two can disagree: the count
 * can be non-zero while the engine is still compiling, and a user who sees "0 blocked" deserves to
 * know whether that means "nothing to block" or "not blocking yet".
 */
@Composable
private fun BlockingSummary(
    sessionBlockedCount: Int,
    blockingEnabled: Boolean,
    engineReady: Boolean,
    engineAvailable: Boolean,
    onClick: () -> Unit
) {
    val headline = when {
        !blockingEnabled -> "Blocking is off"
        !engineAvailable -> "Limited blocking"
        !engineReady -> "Starting the blocker"
        sessionBlockedCount > 0 -> "$sessionBlockedCount blocked this session"
        else -> "Nothing blocked yet"
    }
    val detail = when {
        !blockingEnabled ->
            "Ads and trackers are being allowed through. Tap to turn blocking back on."
        !engineAvailable ->
            "This build has no native blocking library, so only the small built-in host list is " +
                "in use. Tap for details."
        !engineReady ->
            "The filter lists are still compiling. The built-in host list is covering until then."
        sessionBlockedCount > 0 ->
            "EasyList and EasyPrivacy are loaded and intercepting requests. Tap to review."
        else ->
            "EasyList and EasyPrivacy are loaded. Requests are being checked on every page."
    }

    GlassmorphicSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (blockingEnabled && engineAvailable) {
                            HeliosShieldGreen.copy(alpha = 0.16f)
                        } else {
                            HeliosTextTertiary.copy(alpha = 0.14f)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = HeliosIcons.Shield,
                    contentDescription = null,
                    tint = if (blockingEnabled && engineAvailable) {
                        HeliosShieldGreen
                    } else {
                        HeliosTextTertiary
                    },
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = headline,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = HeliosTextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = detail,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = HeliosTextSecondary
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    onActionClick: (() -> Unit)?,
    actionLabel: String?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = HeliosTextSecondary
        )
        if (onActionClick != null && actionLabel != null) {
            Text(
                text = actionLabel,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = HeliosBlue,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onActionClick)
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            )
        }
    }
}

@Composable
private fun SearchEngineRow(
    engines: List<SearchEngine>,
    selected: SearchEngine,
    onSelect: (SearchEngine) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(engines) { engine ->
            val isSelected = engine == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isSelected) HeliosSun.copy(alpha = 0.16f) else HeliosDarkCard)
                    .border(
                        width = 1.dp,
                        color = if (isSelected) HeliosSun.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .clickable { onSelect(engine) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = engine.title,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) HeliosSun else HeliosTextPrimary
                )
            }
        }
    }
}

@Composable
private fun QuickLinkTile(
    label: String,
    count: Int,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(HeliosDarkCard)
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = HeliosSun,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = HeliosTextPrimary
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = if (count == 0) "Nothing saved yet" else "$count saved",
            fontSize = 11.sp,
            color = HeliosTextTertiary
        )
    }
}

@Composable
private fun ShortcutTile(
    title: String,
    initial: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(88.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(HeliosDarkCard)
            .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(HeliosSun.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initial,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = HeliosSun
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = HeliosTextPrimary,
                maxLines = 1
            )
        }
    }
}
