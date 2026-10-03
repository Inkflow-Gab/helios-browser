package com.helios.browser.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helios.browser.data.TabModel
import com.helios.browser.ui.icons.HeliosIcons
import com.helios.browser.ui.theme.HeliosBlue
import com.helios.browser.ui.theme.HeliosShieldGreen
import com.helios.browser.ui.theme.HeliosShieldOrange
import com.helios.browser.ui.theme.HeliosTextPrimary
import com.helios.browser.ui.theme.HeliosTextSecondary
import com.helios.browser.ui.theme.HeliosTextTertiary

@Composable
fun AddressBar(
    currentTab: TabModel,
    tabsCount: Int,
    onNavigate: (String) -> Unit,
    onReload: () -> Unit,
    onOpenShields: () -> Unit,
    onOpenTabs: () -> Unit,
    onOpenMenu: () -> Unit,
    onSwipeNextTab: () -> Unit,
    onSwipePreviousTab: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isEditing by remember { mutableStateOf(false) }
    var inputText by remember(currentTab.url) { mutableStateOf(if (currentTab.url == "helios://start") "" else currentTab.url) }
    val focusManager = LocalFocusManager.current
    var totalDragX by remember { mutableFloatStateOf(0f) }

    val displayHost = remember(currentTab.url) {
        if (currentTab.url == "helios://start" || currentTab.url.isEmpty()) {
            "Search or type URL"
        } else {
            try {
                val uri = android.net.Uri.parse(currentTab.url)
                uri.host ?: currentTab.url
            } catch (_: Exception) {
                currentTab.url
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // Progress bar on page loading
        AnimatedVisibility(
            visible = currentTab.isLoading,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp)),
                color = HeliosBlue,
                trackColor = Color.Transparent
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        GlassmorphicSurface(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { totalDragX = 0f },
                        onDragEnd = {
                            if (totalDragX > 80f) {
                                onSwipePreviousTab()
                            } else if (totalDragX < -80f) {
                                onSwipeNextTab()
                            }
                            totalDragX = 0f
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            totalDragX += dragAmount
                        }
                    )
                },
            shape = RoundedCornerShape(26.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Shields button with live count badge
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onOpenShields
                        )
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = HeliosIcons.Shield,
                            contentDescription = "Shields",
                            tint = if (currentTab.blockedAdsCount > 0) HeliosShieldGreen else HeliosTextSecondary,
                            modifier = Modifier.size(19.dp)
                        )
                        if (currentTab.blockedAdsCount > 0) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = currentTab.blockedAdsCount.toString(),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = HeliosShieldGreen
                            )
                        }
                    }
                }

                // Middle address / search pill
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .clickable { isEditing = true }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (isEditing) {
                        BasicTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(
                                color = HeliosTextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            cursorBrush = SolidColor(HeliosBlue),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(
                                onGo = {
                                    if (inputText.isNotBlank()) {
                                        onNavigate(inputText)
                                    }
                                    isEditing = false
                                    focusManager.clearFocus()
                                }
                            )
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (currentTab.url.startsWith("https://")) {
                                Icon(
                                    imageVector = HeliosIcons.Lock,
                                    contentDescription = "Secure",
                                    tint = HeliosTextSecondary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(
                                text = displayHost,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (currentTab.url == "helios://start") HeliosTextTertiary else HeliosTextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Reload or close editing button
                IconButton(
                    onClick = {
                        if (isEditing) {
                            isEditing = false
                            focusManager.clearFocus()
                        } else {
                            onReload()
                        }
                    },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = if (isEditing) HeliosIcons.Close else HeliosIcons.Refresh,
                        contentDescription = if (isEditing) "Cancel" else "Reload",
                        tint = HeliosTextSecondary,
                        modifier = Modifier.size(17.dp)
                    )
                }

                // Tab Switcher button with tab counter badge
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onOpenTabs),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(Color.White.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tabsCount.coerceAtLeast(1).toString(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = HeliosTextPrimary
                        )
                    }
                }

                // Menu button
                IconButton(
                    onClick = onOpenMenu,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = HeliosIcons.More,
                        contentDescription = "Menu",
                        tint = HeliosTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
