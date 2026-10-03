package com.helios.browser.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helios.browser.ui.icons.HeliosIcons
import com.helios.browser.ui.theme.HeliosBlue
import com.helios.browser.ui.theme.HeliosDarkCard
import com.helios.browser.ui.theme.HeliosPurple
import com.helios.browser.ui.theme.HeliosShieldGreen
import com.helios.browser.ui.theme.HeliosTextPrimary
import com.helios.browser.ui.theme.HeliosTextSecondary

@Composable
fun WelcomeCard(
    modifier: Modifier = Modifier,
    totalBlockedAds: Int = 0
) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)) +
                slideInVertically(
                    initialOffsetY = { -40 },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )
    ) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            HeliosDarkCard,
                            Color(0xFF13151A)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            HeliosBlue.copy(alpha = 0.4f),
                            HeliosPurple.copy(alpha = 0.2f),
                            Color.White.copy(alpha = 0.05f)
                        )
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Top row with emblem and beta chip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(HeliosBlue.copy(alpha = 0.15f))
                                .border(1.dp, HeliosBlue.copy(alpha = 0.3f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = HeliosIcons.Sparkle,
                                contentDescription = "Helios Symbol",
                                tint = HeliosBlue,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Helios Browser",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = HeliosTextPrimary
                        )
                    }

                    // Beta Tag
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(HeliosPurple.copy(alpha = 0.2f))
                            .border(0.5.dp, HeliosPurple.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "BETA v0.1",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = HeliosPurple
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Welcome Title
                Text(
                    text = "Hello, welcome to Helios Browser",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = HeliosTextPrimary,
                    lineHeight = 24.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Creator Subtitle
                Text(
                    text = "this is just beta so dont expect to much and made by 17y/o with only using phone and github",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    color = HeliosTextSecondary,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Shield status banner
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF0F1116))
                        .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = HeliosIcons.Shield,
                        contentDescription = "Shield Active",
                        tint = HeliosShieldGreen,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Helios Shields Active",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = HeliosShieldGreen
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "$totalBlockedAds Blocked",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = HeliosTextPrimary
                    )
                }
            }
        }
    }
}
