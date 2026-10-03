package com.helios.browser.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.helios.browser.domain.model.AppSettings
import com.helios.browser.domain.model.OmniboxPosition
import com.helios.browser.domain.model.SearchEngine
import com.helios.browser.ui.adaptive.HeliosLayout
import com.helios.browser.ui.components.GlassmorphicSurface
import com.helios.browser.ui.icons.HeliosGlyphs
import com.helios.browser.ui.icons.HeliosIcons
import com.helios.browser.ui.splash.HeliosSunMark
import com.helios.browser.ui.theme.HeliosDarkCard
import com.helios.browser.ui.theme.HeliosOledBackground
import com.helios.browser.ui.theme.HeliosShieldGreen
import com.helios.browser.ui.theme.HeliosSun
import com.helios.browser.ui.theme.HeliosTextPrimary
import com.helios.browser.ui.theme.HeliosTextSecondary
import com.helios.browser.ui.theme.HeliosTextTertiary
import kotlin.math.abs

/**
 * First-run setup. Five pages: welcome, privacy defaults, a permission explainer, search engine,
 * and a summary the user can act on before finishing.
 *
 * The permission page deliberately asks for nothing. Every runtime permission in this app is
 * just-in-time — a site has to request the camera before the system dialog appears — so the page's
 * whole job is to make that promise legible before the user has to trust it. Requesting permissions
 * here instead would be the exact behaviour this app is arguing against.
 *
 * @param layout window shape. Setup caps its column on wide screens rather than stretching text
 *   to 1200dp, which is the whole reason this screen takes it instead of measuring itself.
 */
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    layout: HeliosLayout = HeliosLayout.from(360, 780, preferTopOmnibox = false),
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Writing the settings flips DataStore's onboardingCompleted, which comes back through
    // OnboardingState.completed and lets the host swap this screen for the browser.
    LaunchedEffect(state.completed) {
        if (state.completed) onFinished()
    }

    // Page travel direction, set before the intent is dispatched. Read by the LaunchedEffect below
    // after the page index has already changed, which is why a plain remembered Int is enough and
    // rememberUpdatedState is not needed.
    var travelDirection by remember { mutableIntStateOf(1) }
    val slide = remember { Animatable(0f) }
    LaunchedEffect(state.page) {
        slide.snapTo(travelDirection.toFloat())
        slide.animateTo(0f, tween(durationMillis = 300, easing = FastOutSlowInEasing))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HeliosOledBackground)
            .systemBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OnboardingHeader(
            page = state.page,
            showSkip = !state.isLastPage,
            onSkip = { viewModel.onIntent(OnboardingIntent.Skip) },
            modifier = Modifier.widthIn(max = layout.maxContentWidth)
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = layout.maxContentWidth)
                    .fillMaxWidth()
                    // The page arrives slightly offset in the direction of travel and settles to
                    // zero. Read inside the layer block so this animates in the draw phase instead
                    // of recomposing every page on every frame.
                    .graphicsLayer {
                        val fraction = slide.value
                        translationX = fraction * PAGE_SLIDE_DP.dp.toPx()
                        alpha = 1f - abs(fraction) * 0.45f
                    }
            ) {
                when (state.page) {
                    OnboardingState.PAGE_WELCOME -> WelcomePage()

                    OnboardingState.PAGE_PRIVACY -> PrivacyPage(
                        draft = state.draft,
                        onToggleBlocking = { viewModel.onIntent(OnboardingIntent.SetBlockAds(it)) },
                        onToggleCosmetic = { viewModel.onIntent(OnboardingIntent.SetCosmeticFilters(it)) },
                        onToggleHttps = { viewModel.onIntent(OnboardingIntent.SetUpgradeHttps(it)) },
                        onToggleSafeBrowsing = { viewModel.onIntent(OnboardingIntent.SetSafeBrowsing(it)) },
                        onToggleHistory = { viewModel.onIntent(OnboardingIntent.SetSaveHistory(it)) }
                    )

                    OnboardingState.PAGE_PERMISSIONS -> PermissionsPage()

                    OnboardingState.PAGE_SEARCH -> SearchEnginePage(
                        selected = state.draft.searchEngine,
                        onSelect = { viewModel.onIntent(OnboardingIntent.SetSearchEngine(it)) }
                    )

                    else -> SummaryPage(
                        draft = state.draft,
                        onToggleHistory = { viewModel.onIntent(OnboardingIntent.SetSaveHistory(it)) },
                        onSelectOmnibox = {
                            viewModel.onIntent(OnboardingIntent.SetOmniboxPosition(it))
                        }
                    )
                }
            }
        }

        OnboardingFooter(
            state = state,
            onNext = {
                travelDirection = 1
                viewModel.onIntent(OnboardingIntent.Next)
            },
            onBack = {
                travelDirection = -1
                viewModel.onIntent(OnboardingIntent.Back)
            },
            onFinish = { viewModel.onIntent(OnboardingIntent.Finish) },
            modifier = Modifier.widthIn(max = layout.maxContentWidth)
        )
    }
}

@Composable
private fun OnboardingHeader(
    page: Int,
    showSkip: Boolean,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(OnboardingState.PAGE_COUNT) { index ->
                Box(
                    modifier = Modifier
                        .height(4.dp)
                        .width(if (index == page) 22.dp else 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (index <= page) HeliosSun else HeliosTextTertiary.copy(alpha = 0.35f)
                        )
                )
            }
        }
        AnimatedVisibility(visible = showSkip, enter = fadeIn(), exit = fadeOut()) {
            TextButton(onClick = onSkip) {
                Text(
                    text = "Skip",
                    color = HeliosTextSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun WelcomePage() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // The animated mark, matching the launcher icon rather than a generic placeholder glyph.
        HeliosSunMark(modifier = Modifier.size(104.dp))

        Spacer(modifier = Modifier.height(26.dp))
        Text(
            text = "Welcome to Helios",
            fontSize = 27.sp,
            fontWeight = FontWeight.Bold,
            color = HeliosTextPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "A private browser built to stay quiet. Ad networks are cut at the network " +
                "layer before data is transferred, tracking parameters are stripped as you " +
                "follow links, and nothing is uploaded anywhere.",
            fontSize = 15.sp,
            lineHeight = 22.sp,
            color = HeliosTextSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(28.dp))
        FeatureRow(
            icon = HeliosIcons.Shield,
            title = "Network-level blocking",
            body = "Requests to known ad and tracker hosts are dropped before bytes move."
        )
        FeatureRow(
            icon = HeliosIcons.Lock,
            title = "HTTPS by default",
            body = "Plain HTTP links are upgraded automatically."
        )
        FeatureRow(
            icon = HeliosIcons.Incognito,
            title = "Real private tabs",
            body = "Incognito sessions are never written to history or disk."
        )
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun FeatureRow(icon: ImageVector, title: String, body: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(HeliosDarkCard),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = HeliosSun,
                modifier = Modifier.size(19.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = HeliosTextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = body,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = HeliosTextSecondary
            )
        }
    }
}

/**
 * Explains what each runtime permission unlocks and states plainly that nothing is being requested
 * yet. Nothing on this page can be toggled, which is the point: there is no "allow all" gesture to
 * hand out by accident.
 */
@Composable
private fun PermissionsPage() {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionTitle(
            title = "Nothing is asked yet",
            body = "Android only lets a browser use the camera, microphone or location if the " +
                "page you are looking at asks for it. Helios never asks on its own."
        )
        Spacer(modifier = Modifier.height(20.dp))

        PermissionRow(
            icon = HeliosGlyphs.Camera,
            title = "Camera",
            body = "Needed only by sites with a photo or video capture feature, like a document " +
                "scanner. Helios shows its own prompt first."
        )
        PermissionRow(
            icon = HeliosGlyphs.Microphone,
            title = "Microphone",
            body = "Only for sites with voice input, a call, or a recording. Denied means the " +
                "page's record button does nothing."
        )
        PermissionRow(
            icon = HeliosGlyphs.Location,
            title = "Location",
            body = "Only for maps and store finders, and only while you are looking at them. " +
                "Helios declines the request outright in private tabs."
        )
        PermissionRow(
            icon = HeliosGlyphs.Bell,
            title = "Notifications",
            body = "Used so a download can show progress, since the system download service posts " +
                "on Helios's behalf. Asked the first time you download something."
        )

        Spacer(modifier = Modifier.height(20.dp))

        GlassmorphicSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp)
        ) {
            Row(
                modifier = Modifier.padding(18.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = HeliosGlyphs.Info,
                    contentDescription = null,
                    tint = HeliosShieldGreen,
                    modifier = Modifier.size(19.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Every one of these can be refused and the rest of the browser keeps " +
                        "working. A site that cannot see your location is not a broken site, it " +
                        "is a site that did not earn it.",
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = HeliosTextSecondary
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun PermissionRow(icon: ImageVector, title: String, body: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(HeliosDarkCard)
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(HeliosSun.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = HeliosSun,
                modifier = Modifier.size(17.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = HeliosTextPrimary
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = body,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = HeliosTextSecondary
            )
        }
    }
}

@Composable
private fun PrivacyPage(
    draft: AppSettings,
    onToggleBlocking: (Boolean) -> Unit,
    onToggleCosmetic: (Boolean) -> Unit,
    onToggleHttps: (Boolean) -> Unit,
    onToggleSafeBrowsing: (Boolean) -> Unit,
    onToggleHistory: (Boolean) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionTitle(
            title = "Privacy defaults",
            body = "These are just starting values. Change any of them later from the Shields sheet."
        )
        Spacer(modifier = Modifier.height(20.dp))

        SetupToggleRow(
            title = "Block ads and trackers",
            subtitle = "Drops requests to known ad and telemetry hosts",
            checked = draft.blockAdsAndTrackers,
            onCheckedChange = onToggleBlocking,
            recommended = true
        )
        SetupToggleRow(
            title = "Hide ad elements",
            subtitle = "Collapses empty banners and ad wrappers",
            checked = draft.cosmeticFilters,
            onCheckedChange = onToggleCosmetic,
            recommended = true
        )
        SetupToggleRow(
            title = "Upgrade to HTTPS",
            subtitle = "Rewrites plain HTTP links before loading",
            checked = draft.upgradeToHttps,
            onCheckedChange = onToggleHttps,
            recommended = true
        )
        SetupToggleRow(
            title = "Safe Browsing",
            subtitle = "Warns about malware and phishing pages",
            checked = draft.safeBrowsingEnabled,
            onCheckedChange = onToggleSafeBrowsing
        )
        SetupToggleRow(
            title = "Keep browsing history",
            subtitle = "Off means Helios stores nothing about where you went",
            checked = draft.saveHistoryEnabled,
            onCheckedChange = onToggleHistory
        )
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun SearchEnginePage(
    selected: SearchEngine,
    onSelect: (SearchEngine) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionTitle(
            title = "Pick a search engine",
            body = "Used when you type something that is not a URL. You can change it any time."
        )
        Spacer(modifier = Modifier.height(20.dp))
        SearchEngine.entries.forEach { engine ->
            val isSelected = engine == selected
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (isSelected) HeliosSun.copy(alpha = 0.12f) else HeliosDarkCard)
                    .border(
                        width = 1.dp,
                        color = if (isSelected) HeliosSun else Color.White.copy(alpha = 0.07f),
                        shape = RoundedCornerShape(18.dp)
                    )
                    .clickable { onSelect(engine) }
                    .padding(horizontal = 16.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) HeliosSun.copy(alpha = 0.2f)
                            else Color.White.copy(alpha = 0.05f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = HeliosIcons.Search,
                        contentDescription = null,
                        tint = if (isSelected) HeliosSun else HeliosTextSecondary,
                        modifier = Modifier.size(17.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = engine.title,
                        fontSize = 15.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = HeliosTextPrimary
                    )
                    Text(
                        text = engine.homeUrl,
                        fontSize = 11.sp,
                        color = HeliosTextTertiary
                    )
                }
                AnimatedVisibility(visible = isSelected, enter = fadeIn(), exit = fadeOut()) {
                    Icon(
                        imageVector = HeliosGlyphs.Check,
                        contentDescription = "Selected",
                        tint = HeliosSun,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun SummaryPage(
    draft: AppSettings,
    onToggleHistory: (Boolean) -> Unit,
    onSelectOmnibox: (OmniboxPosition) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionTitle(
            title = "You are all set",
            body = "Here is what Helios will do out of the box."
        )
        Spacer(modifier = Modifier.height(20.dp))

        GlassmorphicSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                SummaryRow("Search engine", draft.searchEngine.title)
                SummaryDivider()
                SummaryRow("Ad and tracker blocking", if (draft.blockAdsAndTrackers) "On" else "Off")
                SummaryDivider()
                SummaryRow("Ad element hiding", if (draft.cosmeticFilters) "On" else "Off")
                SummaryDivider()
                SummaryRow("HTTPS upgrade", if (draft.upgradeToHttps) "On" else "Off")
                SummaryDivider()
                SummaryRow("Safe Browsing", if (draft.safeBrowsingEnabled) "On" else "Off")
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        GlassmorphicSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Browsing history",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = HeliosTextPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (draft.saveHistoryEnabled) {
                                "Pages you visit are listed in History so you can come back to them."
                            } else {
                                "Helios will not remember the pages you visit."
                            },
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            color = HeliosTextSecondary
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = draft.saveHistoryEnabled,
                        onCheckedChange = onToggleHistory,
                        colors = heliosSwitchColors()
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))
                SummaryDivider()
                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "Address bar position",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = HeliosTextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Bottom is easier to reach one-handed. On a tablet in landscape it " +
                        "moves to the top automatically.",
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = HeliosTextSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))
                OmniboxPositionPicker(
                    selected = draft.omniboxPosition,
                    onSelect = onSelectOmnibox
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Incognito is always available from the menu, and never records anything.",
            fontSize = 12.sp,
            lineHeight = 17.sp,
            color = HeliosTextTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(24.dp))
    }
}

/** Two-way segmented control. Shared shape with the menu sheet's version so they look identical. */
@Composable
private fun OmniboxPositionPicker(
    selected: OmniboxPosition,
    onSelect: (OmniboxPosition) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        OmniboxPosition.entries.forEach { position ->
            val isSelected = position == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (isSelected) HeliosSun.copy(alpha = 0.18f) else Color.Transparent)
                    .clickable { onSelect(position) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = position.label,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) HeliosSun else HeliosTextSecondary
                )
            }
        }
    }
}

@Composable
private fun OnboardingFooter(
    state: OnboardingState,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(bottom = 20.dp, top = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.page > 0) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = HeliosTextSecondary)
                ) {
                    Text("Back", fontWeight = FontWeight.SemiBold)
                }
            }
            Button(
                onClick = if (state.isLastPage) onFinish else onNext,
                modifier = Modifier
                    .weight(if (state.page > 0) 1.4f else 1f)
                    .height(50.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = HeliosSun,
                    contentColor = HeliosOledBackground
                )
            ) {
                Text(
                    text = if (state.isLastPage) "Start browsing" else "Continue",
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (state.isLastPage) {
                "You can change all of this later."
            } else {
                "Nothing here is permanent."
            },
            fontSize = 11.sp,
            color = HeliosTextTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SectionTitle(title: String, body: String) {
    Text(
        text = title,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        color = HeliosTextPrimary
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = body,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        color = HeliosTextSecondary
    )
}

@Composable
private fun SetupToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    recommended: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(HeliosDarkCard)
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = HeliosTextPrimary
                )
                if (recommended) {
                    Spacer(modifier = Modifier.width(7.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(HeliosShieldGreen.copy(alpha = 0.16f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "ON BY DEFAULT",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = HeliosShieldGreen
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = subtitle,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = HeliosTextSecondary
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = heliosSwitchColors()
        )
    }
}

@Composable
private fun heliosSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = Color.White,
    checkedTrackColor = HeliosShieldGreen,
    uncheckedThumbColor = HeliosTextSecondary,
    uncheckedTrackColor = Color.White.copy(alpha = 0.1f)
)

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = HeliosTextSecondary
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = HeliosTextPrimary
        )
    }
}

@Composable
private fun SummaryDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color.White.copy(alpha = 0.06f))
    )
}

/** How far a page starts out offset when it slides in. */
private const val PAGE_SLIDE_DP = 44