package com.helios.browser.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helios.browser.data.TabModel
import com.helios.browser.ui.icons.HeliosIcons
import com.helios.browser.ui.theme.HeliosBlue
import com.helios.browser.ui.theme.HeliosDarkCard
import com.helios.browser.ui.theme.HeliosDarkSurface
import com.helios.browser.ui.theme.HeliosOledBackground
import com.helios.browser.ui.theme.HeliosPrivateAccent
import com.helios.browser.ui.theme.HeliosPrivateBackground
import com.helios.browser.ui.theme.HeliosShieldGreen
import com.helios.browser.ui.theme.HeliosTextPrimary
import com.helios.browser.ui.theme.HeliosTextSecondary

@Composable
fun TabSwitcher(
    tabs: List<TabModel>,
    currentTabId: String,
    isPrivateMode: Boolean,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onNewTab: (Boolean) -> Unit,
    onCloseAllTabs: () -> Unit,
    onTogglePrivateMode: (Boolean) -> Unit,
    onCloseSwitcher: () -> Unit
) {
    val filteredTabs = remember(tabs, isPrivateMode) {
        tabs.filter { it.isIncognito == isPrivateMode }
    }

    val backgroundColor = if (isPrivateMode) HeliosPrivateBackground else HeliosOledBackground

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 16.dp)
        ) {
            // Top Navigation Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Tab Mode Selector (Regular vs Private)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(HeliosDarkCard)
                        .padding(3.dp)
                ) {
                    TabModeButton(
                        title = "Tabs",
                        icon = HeliosIcons.Tabs,
                        selected = !isPrivateMode,
                        activeColor = HeliosBlue,
                        onClick = { onTogglePrivateMode(false) }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    TabModeButton(
                        title = "Private",
                        icon = HeliosIcons.Incognito,
                        selected = isPrivateMode,
                        activeColor = HeliosPrivateAccent,
                        onClick = { onTogglePrivateMode(true) }
                    )
                }

                // Done Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable(onClick = onCloseSwitcher)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Done",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = HeliosBlue
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Tab Cards Grid
            if (filteredTabs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = if (isPrivateMode) HeliosIcons.Incognito else HeliosIcons.Tabs,
                            contentDescription = null,
                            tint = HeliosTextSecondary,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (isPrivateMode) "No Private Tabs" else "No Open Tabs",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = HeliosTextSecondary
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(filteredTabs, key = { it.id }) { tab ->
                        TabCardItem(
                            tab = tab,
                            isSelected = tab.id == currentTabId,
                            isPrivateMode = isPrivateMode,
                            onClick = { onSelectTab(tab.id) },
                            onClose = { onCloseTab(tab.id) }
                        )
                    }
                }
            }

            // Bottom Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Close all tabs button
                IconButton(
                    onClick = onCloseAllTabs,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = HeliosIcons.Trash,
                        contentDescription = "Close All Tabs",
                        tint = HeliosTextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Text(
                    text = "${filteredTabs.size} Tabs",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = HeliosTextSecondary
                )

                // Plus / New Tab button
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(if (isPrivateMode) HeliosPrivateAccent else HeliosBlue)
                        .clickable { onNewTab(isPrivateMode) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = HeliosIcons.Plus,
                        contentDescription = "New Tab",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TabModeButton(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    val bg = if (selected) activeColor.copy(alpha = 0.25f) else Color.Transparent
    val tint = if (selected) activeColor else HeliosTextSecondary

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = tint,
            modifier = Modifier.size(15.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) HeliosTextPrimary else HeliosTextSecondary
        )
    }
}

@Composable
private fun TabCardItem(
    tab: TabModel,
    isSelected: Boolean,
    isPrivateMode: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit
) {
    val borderColor = if (isSelected) {
        if (isPrivateMode) HeliosPrivateAccent else HeliosBlue
    } else {
        Color.White.copy(alpha = 0.08f)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(HeliosDarkCard)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Card Title Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.04f))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (tab.title.isBlank() || tab.url == "helios://start") "Start Page" else tab.title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = HeliosTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(6.dp))

                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.1f))
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = HeliosIcons.Close,
                        contentDescription = "Close",
                        tint = HeliosTextSecondary,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }

            // Card Body Preview
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (tab.isIncognito) HeliosIcons.Incognito else HeliosIcons.Tabs,
                        contentDescription = null,
                        tint = HeliosTextSecondary.copy(alpha = 0.4f),
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (tab.url == "helios://start") "Helios Start" else tab.url,
                        fontSize = 11.sp,
                        color = HeliosTextSecondary.copy(alpha = 0.7f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
