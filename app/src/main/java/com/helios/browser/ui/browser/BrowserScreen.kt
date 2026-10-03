package com.helios.browser.ui.browser

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.helios.browser.domain.model.BlockingConfig
import com.helios.browser.domain.model.BrowserTab
import com.helios.browser.engine.ExternalUrlLauncher
import com.helios.browser.engine.WebPermissionHost
import com.helios.browser.engine.WebViewFactory
import com.helios.browser.ui.adaptive.HeliosLayout
import com.helios.browser.ui.components.AddressBar
import com.helios.browser.ui.components.BookmarksSheet
import com.helios.browser.ui.components.BrowserMenuSheet
import com.helios.browser.ui.components.CrashBanner
import com.helios.browser.ui.components.CreditsSheet
import com.helios.browser.ui.components.HistorySheet
import com.helios.browser.ui.components.ShieldsSheet
import com.helios.browser.ui.components.StartPageView
import com.helios.browser.ui.components.TabSwitcher
import com.helios.browser.ui.icons.HeliosIcons
import com.helios.browser.ui.theme.HeliosOledBackground
import com.helios.browser.ui.theme.HeliosPrivateBackground
import com.helios.browser.ui.theme.HeliosSun
import com.helios.browser.ui.theme.HeliosTextSecondary
import com.helios.browser.ui.theme.HeliosTextTertiary

/**
 * The only place in the app that owns `WebView` instances.
 *
 * State lives in [BrowserViewModel]; this composable translates intents into WebView calls and
 * WebView callbacks back into intents. WebViews are cached per tab in [webViews] because
 * `AndroidView` recreates its content when the composable leaves the tree, which would otherwise
 * throw away scroll position and history on every overlay change.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel,
    layout: HeliosLayout = HeliosLayout.from(360, 780, preferTopOmnibox = false)
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val webViews = remember { mutableMapOf<String, WebView>() }
    val currentTab = state.currentTab

    // Permission plumbing needs an Activity result launcher, so it lives here rather than in the
    // ViewModel. See WebPermissionHost.
    var pendingWebPermission by remember { mutableStateOf<PermissionRequest?>(null) }
    var pendingFileCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    var pendingGeolocation by remember { mutableStateOf<Pair<String, GeolocationPermissions.Callback>?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        val webPermission = pendingWebPermission
        val geolocation = pendingGeolocation
        val allGranted = granted.values.all { it }

        if (webPermission != null) {
            if (allGranted) webPermission.grant(webPermission.resources) else webPermission.deny()
            pendingWebPermission = null
        }
        if (geolocation != null) {
            geolocation.second.invoke(geolocation.first, allGranted, false)
            pendingGeolocation = null
        }
    }

    val fileChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        val callback = pendingFileCallback
        if (callback != null) {
            callback.onReceiveValue(uri?.let { arrayOf(it) })
            pendingFileCallback = null
        }
    }

    val downloadPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        viewModel.onIntent(BrowserIntent.DownloadPermissionResult(granted.values.all { it }))
    }

    val permissionHost = remember {
        object : WebPermissionHost {
            override fun onWebPermissionRequest(request: PermissionRequest) {
                pendingWebPermission = request
                permissionLauncher.launch(webPermissionsFor(request.resources))
            }

            override fun onFileChooserRequest(callback: ValueCallback<Array<Uri>>) {
                pendingFileCallback = callback
                runCatching { fileChooserLauncher.launch("*/*") }
                    .onFailure {
                        callback.onReceiveValue(null)
                        pendingFileCallback = null
                    }
            }

            override fun onGeolocationRequest(
                origin: String,
                callback: GeolocationPermissions.Callback
            ) {
                pendingGeolocation = origin to callback
                permissionLauncher.launch(
                    arrayOf(
                        android.Manifest.permission.ACCESS_FINE_LOCATION,
                        android.Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }
    }

    // One-shot effects. Collected here because each needs a WebView, an Activity, or a Context.
    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            // Read the active tab from the ViewModel rather than from `currentTab`: this effect is
            // keyed on Unit, so a captured `currentTab` would be frozen at the tab that was active
            // when the collector started, and every command would hit the wrong WebView.
            val activeTab = viewModel.state.value.currentTab
            val webView = activeTab?.let { webViews[it.id] }
            when (effect) {
                is BrowserEffect.LoadUrl -> webView?.loadUrl(effect.url)

                BrowserEffect.Reload -> webView?.reload()

                BrowserEffect.Stop -> webView?.stopLoading()

                BrowserEffect.GoBack -> webView?.takeIf { it.canGoBack() }?.goBack()

                BrowserEffect.GoForward -> webView?.takeIf { it.canGoForward() }?.goForward()

                is BrowserEffect.LaunchExternalApp -> {
                    val launched = ExternalUrlLauncher.launch(context, effect.url)
                    if (!launched) viewModel.onExternalAppFailed(effect.url)
                }

                is BrowserEffect.SharePage -> {
                    val intent = ExternalUrlLauncher.buildShareIntent(context, effect.url, effect.title)
                    if (intent != null) {
                        runCatching { context.startActivity(intent) }
                    } else {
                        snackbarHostState.showSnackbar("No app can share this page")
                    }
                }

                is BrowserEffect.CopyToClipboard -> {
                    val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("url", effect.text))
                        snackbarHostState.showSnackbar("Link copied")
                    }
                }

                is BrowserEffect.DownloadPermissionRequired ->
                    downloadPermissionLauncher.launch(effect.permissions.toTypedArray())

                BrowserEffect.CreateShortcut ->
                    snackbarHostState.showSnackbar("Home screen shortcuts are not supported yet")

                is BrowserEffect.ShowMessage ->
                    snackbarHostState.showSnackbar(
                        message = effect.message,
                        duration = SnackbarDuration.Short
                    )
            }
        }
    }

    // Back button: close overlays, then let the WebView handle history, then go home.
    BackHandler {
        val overlay = state.overlay
        if (overlay != BrowserOverlay.None) {
            viewModel.onIntent(BrowserIntent.DismissOverlay)
            return@BackHandler
        }
        val webView = currentTab?.let { webViews[it.id] }
        if (webView?.canGoBack() == true) {
            webView.goBack()
        } else if (currentTab != null && !currentTab.isStartPage) {
            viewModel.onIntent(BrowserIntent.GoHome)
        }
    }

    val backgroundColor = if (state.isPrivateMode) HeliosPrivateBackground else HeliosOledBackground

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        // A permanent rail on wide screens; a plain Column everywhere else. The rail is the one
        // structural change a tablet gets, because it is what makes the extra width usable instead
        // of just making everything more spread out.
        if (layout.useNavigationRail) {
            Row(modifier = Modifier.fillMaxSize()) {
                BrowserNavigationRail(
                    tabCount = state.visibleTabs.size,
                    onTabsClick = { viewModel.onIntent(BrowserIntent.ShowOverlay(BrowserOverlay.Tabs)) },
                    onMenuClick = {
                        viewModel.onIntent(BrowserIntent.ShowOverlay(BrowserOverlay.Menu))
                    },
                    onShieldsClick = {
                        viewModel.onIntent(BrowserIntent.ShowOverlay(BrowserOverlay.Shields))
                    }
                )

                // Start-page content gets a capped reading column; page content does not, because
                // the WebView is the user's content and should own the full area it is given.
                if (currentTab == null || currentTab.isStartPage) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            StartPageView(
                                state = state,
                                layout = layout,
                                onIntent = viewModel::onIntent,
                                modifier = Modifier.widthIn(max = layout.maxContentWidth)
                            )
                        }
                        AddressBar(
                            tab = currentTab,
                            tabCount = state.visibleTabs.size,
                            isBookmarked = state.isCurrentBookmarked,
                            swipePreviousAvailable = state.visibleTabs
                                .indexOfFirst { it.id == state.currentTabId } > 0,
                            swipeNextAvailable = state.visibleTabs
                                .indexOfFirst { it.id == state.currentTabId }
                                .let { it >= 0 && it < state.visibleTabs.size - 1 },
                            layout = layout,
                            onIntent = viewModel::onIntent
                        )
                    }
                } else {
                    BrowserSurface(
                        state = state,
                        currentTab = currentTab,
                        webViews = webViews,
                        permissionHost = permissionHost,
                        blockingConfigProvider = { viewModel.blockingConfig },
                        layout = layout,
                        onIntent = viewModel::onIntent,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                // A crash on the previous run is surfaced here rather than as a launch-blocking
                // dialog: a beta browser that refuses to start is worse than one that starts and
                // explains itself. The banner is dismissible and the trace stays on disk.
                CrashBanner(onDismiss = {})
                if (layout.omniboxAtTop) {
                    AddressBar(
                        tab = currentTab,
                        tabCount = state.visibleTabs.size,
                        isBookmarked = state.isCurrentBookmarked,
                        swipePreviousAvailable = state.visibleTabs
                            .indexOfFirst { it.id == state.currentTabId } > 0,
                        swipeNextAvailable = state.visibleTabs
                            .indexOfFirst { it.id == state.currentTabId }
                            .let { it >= 0 && it < state.visibleTabs.size - 1 },
                        layout = layout,
                        onIntent = viewModel::onIntent
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    BrowserSurface(
                        state = state,
                        currentTab = currentTab,
                        webViews = webViews,
                        permissionHost = permissionHost,
                        blockingConfigProvider = { viewModel.blockingConfig },
                        layout = layout,
                        onIntent = viewModel::onIntent
                    )
                }

                if (!layout.omniboxAtTop) {
                    AddressBar(
                        tab = currentTab,
                        tabCount = state.visibleTabs.size,
                        isBookmarked = state.isCurrentBookmarked,
                        swipePreviousAvailable = state.visibleTabs
                            .indexOfFirst { it.id == state.currentTabId } > 0,
                        swipeNextAvailable = state.visibleTabs
                            .indexOfFirst { it.id == state.currentTabId }
                            .let { it >= 0 && it < state.visibleTabs.size - 1 },
                        layout = layout,
                        onIntent = viewModel::onIntent
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            // Above the omnibox when it is at the bottom, otherwise it would cover the address field
            // it is reporting on.
            modifier = Modifier
                .align(if (layout.omniboxAtTop) Alignment.TopCenter else Alignment.BottomCenter)
                .padding(bottom = if (layout.omniboxAtTop) 0.dp else SNACKBAR_CLEARANCE.dp)
        )

        // Overlays
        when (state.overlay) {
            BrowserOverlay.None -> Unit

            BrowserOverlay.Shields -> ShieldsSheet(
                settings = state.settings,
                tab = currentTab,
                sessionBlockedCount = state.sessionBlockedCount,
                downloadCount = state.downloads.size,
                engine = state.blockingEngine,
                onIntent = viewModel::onIntent,
                onDismiss = { viewModel.onIntent(BrowserIntent.DismissOverlay) }
            )

            BrowserOverlay.Menu -> BrowserMenuSheet(
                tab = currentTab,
                settings = state.settings,
                blockedCount = currentTab?.blockedCount ?: 0,
                onIntent = viewModel::onIntent,
                onDismiss = { viewModel.onIntent(BrowserIntent.DismissOverlay) }
            )

            BrowserOverlay.Bookmarks -> BookmarksSheet(
                bookmarks = state.bookmarks,
                onIntent = viewModel::onIntent,
                onDismiss = { viewModel.onIntent(BrowserIntent.DismissOverlay) }
            )

            BrowserOverlay.History -> HistorySheet(
                entries = state.history,
                onIntent = viewModel::onIntent,
                onDismiss = { viewModel.onIntent(BrowserIntent.DismissOverlay) }
            )

            BrowserOverlay.Tabs -> AnimatedVisibility(
                visible = true,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
            ) {
                TabSwitcher(
                    state = state,
                    layout = layout,
                    onIntent = viewModel::onIntent,
                    onCloseSwitcher = { viewModel.onIntent(BrowserIntent.DismissOverlay) }
                )
            }

            BrowserOverlay.Credits -> CreditsSheet(
                // Dismiss first, then navigate: leaving the sheet up behind a loading page looks
                // like the sheet is part of the destination.
                onOpenUrl = {
                    viewModel.onIntent(BrowserIntent.DismissOverlay)
                    viewModel.onIntent(BrowserIntent.Navigate(it))
                },
                onDismiss = { viewModel.onIntent(BrowserIntent.DismissOverlay) }
            )

            BrowserOverlay.Downloads -> Unit // Downloads are surfaced through snackbars for now.
        }
    }

    // Release tab WebViews that are no longer part of the state.
    LaunchedEffect(state.tabs.map { it.id }) {
        val liveIds = state.tabs.map { it.id }.toSet()
        val stale = webViews.keys.filterNot { it in liveIds }
        stale.forEach { id ->
            webViews.remove(id)?.let { webView ->
                webView.stopLoading()
                webView.destroy()
            }
        }
    }

    // Keep the desktop user agent applied when a tab's desktop flag flips. The blocking config is
    // already refreshed by the ViewModel whenever settings change, so it needs no Compose effect.
    LaunchedEffect(currentTab?.id, currentTab?.isDesktopMode) {
        val tab = currentTab ?: return@LaunchedEffect
        val webView = webViews[tab.id] ?: return@LaunchedEffect
        WebViewFactory.applyDesktopMode(webView, tab.isDesktopMode)
    }
}

/**
 * Either the start page or the active tab's cached WebView.
 *
 * Split out of `BrowserScreen` so the two layouts — rail and column — share one definition of what
 * "the page" is. They previously had to agree implicitly, which is exactly the kind of duplication
 * that drifts.
 *
 * @param modifier applied to whichever branch is drawn; the caller is responsible for sizing it.
 */
@Composable
private fun BrowserSurface(
    state: BrowserState,
    currentTab: BrowserTab?,
    webViews: MutableMap<String, WebView>,
    permissionHost: WebPermissionHost,
    blockingConfigProvider: () -> BlockingConfig,
    layout: HeliosLayout,
    onIntent: (BrowserIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    val tab = currentTab
    if (tab == null || tab.isStartPage) {
        StartPageView(
            state = state,
            layout = layout,
            onIntent = onIntent,
            modifier = modifier
        )
        return
    }

    AndroidView(
        factory = { viewContext ->
            webViews.getOrPut(tab.id) {
                WebViewFactory.create(
                    context = viewContext,
                    isIncognito = tab.isIncognito,
                    isDesktopMode = tab.isDesktopMode,
                    blockingConfigProvider = blockingConfigProvider,
                    permissionHost = permissionHost,
                    downloadListener = { request ->
                        onIntent(BrowserIntent.DownloadRequested(request))
                    },
                    onIntent = onIntent
                )
            }
        },
        update = { webView ->
            // The WebView is cached per tab, so keep its blocked-request listener and privacy mode in
            // sync with the current tab.
            WebViewFactory.syncPrivacy(webView, tab.isIncognito)
            if (webView.url != tab.url && !tab.isStartPage) {
                webView.loadUrl(tab.url)
            }
        },
        modifier = modifier.fillMaxSize()
    )
}

/**
 * Left-hand rail shown on wide screens, where a bottom bar is both an awkward reach and a waste of
 * a full row of page height.
 *
 * Hand-rolled rather than `NavigationRail` because that composable is also experimental in the
 * Material3 version pinned here, and this needs exactly three destinations.
 */
@Composable
private fun BrowserNavigationRail(
    tabCount: Int,
    onTabsClick: () -> Unit,
    onMenuClick: () -> Unit,
    onShieldsClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(76.dp)
            .background(Color.White.copy(alpha = 0.04f))
            .systemBarsPadding()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        RailButton(
            icon = HeliosIcons.Shield,
            label = "Shields",
            onClick = onShieldsClick
        )
        RailButton(
            icon = HeliosIcons.Tabs,
            label = "Tabs",
            badge = tabCount.coerceAtLeast(1).toString(),
            onClick = onTabsClick
        )
        Spacer(Modifier.weight(1f))
        RailButton(
            icon = HeliosIcons.More,
            label = "Menu",
            onClick = onMenuClick
        )
    }
}

@Composable
private fun RailButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    badge: String? = null
) {
    Column(
        modifier = Modifier
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp)
            .widthIn(min = RAIL_TOUCH_TARGET.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = HeliosTextSecondary,
                modifier = Modifier.size(21.dp)
            )
            if (badge != null) {
                Text(
                    text = badge,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = HeliosSun,
                    modifier = Modifier
                        .offset(x = 6.dp, y = (-6).dp)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 9.sp,
            color = HeliosTextTertiary
        )
    }
}

/**
 * How far the snackbar is lifted above a bottom omnibox. Roughly the bar's own height plus its
 * inset padding, so a "Link copied" confirmation never covers the field it copied from.
 */
private const val SNACKBAR_CLEARANCE = 78

/** Minimum width of a rail button, kept at the accessibility floor for touch targets. */
private const val RAIL_TOUCH_TARGET = 48

/** Translates Chromium permission resources into the Android permissions they require. */
private fun webPermissionsFor(resources: Array<String>?): Array<String> {
    val required = mutableListOf<String>()
    resources?.forEach { resource ->
        when (resource) {
            PermissionRequest.RESOURCE_VIDEO_CAPTURE ->
                required.add(android.Manifest.permission.CAMERA)

            PermissionRequest.RESOURCE_AUDIO_CAPTURE ->
                required.add(android.Manifest.permission.RECORD_AUDIO)

            else -> Unit
        }
    }
    // An empty request array means "deny everything", so fall back to a permission that is never
    // granted for this flow rather than launching with nothing.
    return if (required.isEmpty()) {
        arrayOf(android.Manifest.permission.CAMERA)
    } else {
        required.toTypedArray()
    }
}