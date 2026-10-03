package com.helios.browser.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
// `by` on a State<T> needs this; Composable alone does not bring it in.
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helios.browser.domain.model.BrowserTab
import com.helios.browser.ui.adaptive.HeliosLayout
import com.helios.browser.ui.browser.BrowserIntent
import com.helios.browser.ui.browser.BrowserState
import com.helios.browser.ui.icons.HeliosIcons
import com.helios.browser.ui.theme.HeliosBlue
import com.helios.browser.ui.theme.HeliosDarkCard
import com.helios.browser.ui.theme.HeliosOledBackground
import com.helios.browser.ui.theme.HeliosPrivateAccent
import com.helios.browser.ui.theme.HeliosPrivateBackground
import com.helios.browser.ui.theme.HeliosShieldGreen
import com.helios.browser.ui.theme.HeliosTextPrimary
import com.helios.browser.ui.theme.HeliosTextSecondary
import com.helios.browser.ui.theme.HeliosTextTertiary
import com.helios.browser.ui.theme.HeliosPurple

/**
 * Two-column card grid of open tabs, with a private/regular mode toggle.
 *
 * Cards are static: Helios does not snapshot WebViews for previews, so each card shows the page
 * title, host and block count instead of a live thumbnail. Adding real previews would mean calling
 * `drawView` into a bitmap per tab, which costs memory on low-end devices.
 */
@Composable
fun TabSwitcher(
    state: BrowserState,
    layout: HeliosLayout,
    onIntent: (BrowserIntent) -> Unit,
    onCloseSwitcher: () -> Unit
) {
    val backgroundColor = if (state.isPrivateMode) HeliosPrivateBackground else HeliosOledBackground
    val visibleTabs = state.visibleTabs

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(top = 16.dp)
        ) {
            TabSwitcherHeader(
                isPrivateMode = state.isPrivateMode,
                tabCount = visibleTabs.size,
                onTogglePrivate = { onIntent(BrowserIntent.SetPrivateMode(it)) },
                onClose = onCloseSwitcher
            )

            Spacer(modifier = Modifier.height(14.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(layout.tabGridColumns),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(visibleTabs, key = { it.id }) { tab ->
                    TabCard(
                        tab = tab,
                        isCurrent = tab.id == state.currentTabId,
                        isPrivateMode = state.isPrivateMode,
                        minHeight = layout.cardMinHeightDp.dp,
                        onSelect = { onIntent(BrowserIntent.SelectTab(tab.id)) },
                        onClose = { onIntent(BrowserIntent.CloseTab(tab.id)) }
                    )
                }
            }

            TabSwitcherFooter(
                isPrivateMode = state.isPrivateMode,
                onNewTab = { onIntent(BrowserIntent.NewTab) },
                onCloseAll = { onIntent(BrowserIntent.CloseAllTabs) }
            )
        }
    }
}

@Composable
private fun TabSwitcherHeader(
    isPrivateMode: Boolean,
    tabCount: Int,
    onTogglePrivate: (Boolean) -> Unit,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Regular / private segmented toggle
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(HeliosDarkCard)
                .padding(3.dp)
        ) {
            ModeChip(
                label = "Tabs",
                selected = !isPrivateMode,
                accent = HeliosBlue,
                onClick = { onTogglePrivate(false) }
            )
            ModeChip(
                label = "Private",
                selected = isPrivateMode,
                accent = HeliosPrivateAccent,
                onClick = { onTogglePrivate(true) }
            )
        }

        Text(
            text = "$tabCount open",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = HeliosTextTertiary
        )

        IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = HeliosIcons.Close,
                contentDescription = "Close tab switcher",
                tint = HeliosTextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun ModeChip(
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(17.dp))
            .background(if (selected) accent.copy(alpha = 0.18f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) accent else HeliosTextSecondary
        )
    }
}

@Composable
private fun TabCard(
    tab: BrowserTab,
    isCurrent: Boolean,
    isPrivateMode: Boolean,
    minHeight: androidx.compose.ui.unit.Dp,
    onSelect: () -> Unit,
    onClose: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isCurrent) 1f else 0.97f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "tabScale"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            // minHeight rather than height: on a tall screen the grid should give the extra room
            // to the row rather than stretching a two-word title across 400dp.
            .heightIn(min = minHeight)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        if (isPrivateMode) HeliosPurple.copy(alpha = 0.22f) else HeliosBlue.copy(alpha = 0.18f),
                        HeliosDarkCard
                    )
                )
            )
            .border(
                width = if (isCurrent) 1.5.dp else 1.dp,
                color = if (isCurrent) HeliosBlue else Color.White.copy(alpha = 0.07f),
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onSelect)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(if (tab.isIncognito) HeliosPrivateAccent else HeliosBlue)
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onClose, modifier = Modifier.size(26.dp)) {
                Icon(
                    imageVector = HeliosIcons.Close,
                    contentDescription = "Close tab",
                    tint = HeliosTextSecondary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .weight(1f)
        ) {
            Text(
                text = tab.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = HeliosTextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = tab.displayHost,
                fontSize = 10.sp,
                color = HeliosTextTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (tab.isLoading) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${tab.progress}%",
                    fontSize = 10.sp,
                    color = HeliosBlue
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (tab.isIncognito) HeliosIcons.Incognito else HeliosIcons.Lock,
                contentDescription = null,
                tint = if (tab.isSecure) HeliosShieldGreen else HeliosTextTertiary,
                modifier = Modifier.size(13.dp)
            )
            if (tab.blockedCount > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = HeliosIcons.Shield,
                        contentDescription = null,
                        tint = HeliosShieldGreen,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = tab.blockedCount.toString(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = HeliosShieldGreen
                    )
                }
            } else {
                Text(
                    text = if (tab.isSecure) "Secure" else "Not secure",
                    fontSize = 9.sp,
                    color = HeliosTextTertiary
                )
            }
        }
    }
}

@Composable
private fun TabSwitcherFooter(
    isPrivateMode: Boolean,
    onNewTab: () -> Unit,
    onCloseAll: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (isPrivateMode) HeliosPrivateAccent else HeliosBlue)
                .clickable(onClick = onNewTab),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isPrivateMode) "New private tab" else "New tab",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = HeliosTextPrimary
            )
        }

        Box(
            modifier = Modifier
                .width(48.dp)
                .height(48.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(HeliosDarkCard)
                .clickable(onClick = onCloseAll),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = HeliosIcons.Trash,
                contentDescription = "Close all tabs",
                tint = HeliosTextSecondary,
                modifier = Modifier.size(19.dp)
            )
        }
    }
}