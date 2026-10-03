package com.helios.browser.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helios.browser.data.TabModel
import com.helios.browser.ui.icons.HeliosIcons
import com.helios.browser.ui.theme.HeliosBlue
import com.helios.browser.ui.theme.HeliosDarkCard
import com.helios.browser.ui.theme.HeliosDarkSurface
import com.helios.browser.ui.theme.HeliosShieldGreen
import com.helios.browser.ui.theme.HeliosTextPrimary
import com.helios.browser.ui.theme.HeliosTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShieldsSheet(
    currentTab: TabModel,
    onDismiss: () -> Unit,
    onToggleDesktopMode: (Boolean) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var shieldsEnabled by remember { mutableStateOf(true) }
    var cosmeticFiltersEnabled by remember { mutableStateOf(true) }
    var cleanUrlsEnabled by remember { mutableStateOf(true) }

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
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(HeliosShieldGreen.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = HeliosIcons.Shield,
                        contentDescription = "Shields",
                        tint = HeliosShieldGreen,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Helios Shields",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = HeliosTextPrimary
                    )
                    Text(
                        text = "Real-time Ad & Tracker Protection",
                        fontSize = 12.sp,
                        color = HeliosTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Stats Cards Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Blocked metric card
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(HeliosDarkCard)
                        .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Column {
                        Text(
                            text = "${currentTab.blockedAdsCount}",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = HeliosShieldGreen
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Ads Blocked",
                            fontSize = 11.sp,
                            color = HeliosTextSecondary
                        )
                    }
                }

                // Data saved metric card
                val estKbSaved = currentTab.blockedAdsCount * 128
                val dataSavedDisplay = if (estKbSaved > 1024) {
                    String.format("%.1f MB", estKbSaved / 1024f)
                } else {
                    "$estKbSaved KB"
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(HeliosDarkCard)
                        .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Column {
                        Text(
                            text = dataSavedDisplay,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = HeliosBlue
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Data Saved",
                            fontSize = 11.sp,
                            color = HeliosTextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Toggles
            ShieldToggleRow(
                title = "Block Ads and Trackers",
                subtitle = "Drops ad network requests at network layer",
                checked = shieldsEnabled,
                onCheckedChange = { shieldsEnabled = it }
            )

            Spacer(modifier = Modifier.height(12.dp))

            ShieldToggleRow(
                title = "Cosmetic Element Hiding",
                subtitle = "Collapses blank ad wrappers and frames",
                checked = cosmeticFiltersEnabled,
                onCheckedChange = { cosmeticFiltersEnabled = it }
            )

            Spacer(modifier = Modifier.height(12.dp))

            ShieldToggleRow(
                title = "Clean Tracking Parameters",
                subtitle = "Strips utm, fbclid, and gclid from URLs",
                checked = cleanUrlsEnabled,
                onCheckedChange = { cleanUrlsEnabled = it }
            )

            Spacer(modifier = Modifier.height(12.dp))

            ShieldToggleRow(
                title = "Desktop Site Mode",
                subtitle = "Request desktop view for this page",
                checked = currentTab.isDesktopMode,
                onCheckedChange = {
                    currentTab.isDesktopMode = it
                    onToggleDesktopMode(it)
                }
            )

            Spacer(modifier = Modifier.height(30.dp))
        }
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
            .clip(RoundedCornerShape(14.dp))
            .background(HeliosDarkCard)
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
                color = HeliosTextSecondary,
                lineHeight = 15.sp
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = HeliosShieldGreen,
                uncheckedThumbColor = HeliosTextSecondary,
                uncheckedTrackColor = Color.White.copy(alpha = 0.1f)
            )
        )
    }
}
