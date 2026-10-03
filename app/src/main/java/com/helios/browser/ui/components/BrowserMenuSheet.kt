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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helios.browser.domain.model.AppSettings
import com.helios.browser.domain.model.BrowserTab
import com.helios.browser.domain.model.SearchEngine
import com.helios.browser.ui.browser.BrowserIntent
import com.helios.browser.ui.browser.BrowserOverlay
import com.helios.browser.ui.icons.HeliosGlyphs
import com.helios.browser.ui.icons.HeliosIcons
import com.helios.browser.ui.theme.HeliosBlue
import com.helios.browser.ui.theme.HeliosDarkCard
import com.helios.browser.ui.theme.HeliosDarkSurface
import com.helios.browser.ui.theme.HeliosPrivateAccent
import com.helios.browser.ui.theme.HeliosShieldGreen
import com.helios.browser.ui.theme.HeliosTextPrimary
import com.helios.browser.ui.theme.HeliosTextSecondary

/**
 * Menu sheet: tab actions, privacy mode, library entry points, and the search engine picker.
 *
 * Actions are emitted as intents, so nothing here has to know whether a page is loaded.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserMenuSheet(
    tab: BrowserTab?,
    settings: AppSettings,
    onIntent: (BrowserIntent) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val hasRealPage = tab != null && !tab.isStartPage

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
            if (hasRealPage) {
                Text(
                    text = tab.title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = HeliosTextPrimary,
                    maxLines = 2
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (tab.isSecure) HeliosIcons.Lock else HeliosIcons.Sparkle,
                        contentDescription = null,
                        tint = if (tab.isSecure) HeliosShieldGreen else HeliosTextSecondary,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = tab.displayHost,
                        fontSize = 12.sp,
                        color = HeliosTextSecondary,
                        maxLines = 1
                    )
                }
                Spacer(modifier = Modifier.height(18.dp))
            }

            MenuAction(
                icon = HeliosIcons.Plus,
                title = "New tab",
                subtitle = "Open another page in this browsing mode",
                onClick = { onIntent(BrowserIntent.NewTab) }
            )

            MenuAction(
                icon = HeliosIcons.Incognito,
                title = "New private tab",
                subtitle = "Nothing is saved, cookies are blocked",
                tint = HeliosPrivateAccent,
                onClick = { onIntent(BrowserIntent.NewPrivateTab) }
            )

            MenuAction(
                icon = HeliosIcons.Tabs,
                title = "Tab switcher",
                onClick = { onIntent(BrowserIntent.ShowOverlay(BrowserOverlay.Tabs)) }
            )

            MenuAction(
                icon = HeliosGlyphs.Bookmark,
                title = "Bookmarks",
                onClick = { onIntent(BrowserIntent.ShowOverlay(BrowserOverlay.Bookmarks)) }
            )

            MenuAction(
                icon = HeliosGlyphs.History,
                title = "History",
                onClick = { onIntent(BrowserIntent.ShowOverlay(BrowserOverlay.History)) }
            )

            if (hasRealPage) {
                MenuAction(
                    icon = HeliosGlyphs.Globe,
                    title = "Copy link",
                    subtitle = tab.url,
                    onClick = { onIntent(BrowserIntent.CopyLink(tab.url)) }
                )

                MenuAction(
                    icon = HeliosIcons.Sparkle,
                    title = "Share page",
                    subtitle = "Send the title and link to another app",
                    onClick = { onIntent(BrowserIntent.SharePage(tab.url, tab.title)) }
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "Search engine",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = HeliosTextSecondary,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            SearchEngine.entries.forEach { engine ->
                val isSelected = engine == settings.searchEngine
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isSelected) HeliosBlue.copy(alpha = 0.14f) else HeliosDarkCard)
                        .border(
                            width = 1.dp,
                            color = if (isSelected) HeliosBlue else Color.White.copy(alpha = 0.06f),
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable { onIntent(BrowserIntent.SetSearchEngine(engine)) }
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = engine.title,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) HeliosBlue else HeliosTextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    if (isSelected) {
                        Icon(
                            imageVector = HeliosGlyphs.Check,
                            contentDescription = "Selected",
                            tint = HeliosBlue,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuAction(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    tint: Color = HeliosTextPrimary,
    enabled: Boolean = true,
    hidden: Boolean = false,
    onClick: () -> Unit
) {
    if (hidden) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(HeliosDarkCard)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(Color.White.copy(alpha = 0.05f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(17.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = HeliosTextPrimary
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = HeliosTextSecondary,
                    maxLines = 1
                )
            }
        }
    }
}