package com.helios.browser.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.helios.browser.data.DefaultShortcuts
import com.helios.browser.data.SearchEngine
import com.helios.browser.data.TabModel
import com.helios.browser.engine.HeliosWebViewClient
import com.helios.browser.ui.components.AddressBar
import com.helios.browser.ui.components.BrowserMenuSheet
import com.helios.browser.ui.components.ShieldsSheet
import com.helios.browser.ui.components.TabSwitcher
import com.helios.browser.ui.components.WelcomeCard
import com.helios.browser.ui.icons.HeliosIcons
import com.helios.browser.ui.theme.HeliosBlue
import com.helios.browser.ui.theme.HeliosDarkCard
import com.helios.browser.ui.theme.HeliosOledBackground
import com.helios.browser.ui.theme.HeliosPrivateAccent
import com.helios.browser.ui.theme.HeliosPrivateBackground
import com.helios.browser.ui.theme.HeliosShieldGreen
import com.helios.browser.ui.theme.HeliosTextPrimary
import com.helios.browser.ui.theme.HeliosTextSecondary

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen() {
    val context = LocalContext.current

    // Browser State
    val tabs = remember { mutableStateListOf(TabModel()) }
    var currentTabId by remember { mutableStateOf(tabs.first().id) }
    var isPrivateMode by remember { mutableStateOf(false) }
    var selectedSearchEngine by remember { mutableStateOf(SearchEngine.DUCKDUCKGO) }

    // Sheets / Overlay visibility
    var showTabSwitcher by remember { mutableStateOf(false) }
    var showShieldsSheet by remember { mutableStateOf(false) }
    var showMenuSheet by remember { mutableStateOf(false) }

    // Active Tab
    val currentTab = tabs.find { it.id == currentTabId } ?: tabs.first()

    // Map of WebViews per tab ID to maintain tab state & history
    val webViewMap = remember { mutableMapOf<String, WebView>() }

    // Total blocked across session
    var totalSessionBlockedAds by remember { mutableIntStateOf(0) }

    // Intercept hardware / gesture back button
    BackHandler {
        if (showTabSwitcher) {
            showTabSwitcher = false
        } else if (showShieldsSheet) {
            showShieldsSheet = false
        } else if (showMenuSheet) {
            showMenuSheet = false
        } else {
            val activeWebView = webViewMap[currentTab.id]
            if (activeWebView != null && activeWebView.canGoBack()) {
                activeWebView.goBack()
            } else if (currentTab.url != "helios://start") {
                currentTab.url = "helios://start"
            }
        }
    }

    val backgroundColor = if (currentTab.isIncognito) HeliosPrivateBackground else HeliosOledBackground

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Main Web or Start Page Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (currentTab.url == "helios://start" || currentTab.url.isEmpty()) {
                    // Start Page View
                    StartPageView(
                        totalBlocked = totalSessionBlockedAds,
                        selectedSearchEngine = selectedSearchEngine,
                        onSelectSearchEngine = { selectedSearchEngine = it },
                        onSelectShortcut = { url ->
                            currentTab.url = url
                            webViewMap[currentTab.id]?.loadUrl(url)
                        }
                    )
                } else {
                    // Render Android WebView
                    AndroidView(
                        factory = { ctx ->
                            webViewMap.getOrPut(currentTab.id) {
                                WebView(ctx).apply {
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                    settings.apply {
                                        javaScriptEnabled = true
                                        domStorageEnabled = true
                                        databaseEnabled = true
                                        loadWithOverviewMode = true
                                        useWideViewPort = true
                                        setSupportZoom(true)
                                        builtInZoomControls = true
                                        displayZoomControls = false
                                        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                    }

                                    webViewClient = HeliosWebViewClient(
                                        tab = currentTab,
                                        onPageStartedCallback = { _ -> },
                                        onPageFinishedCallback = { _ -> },
                                        onBlockedItemCallback = { count ->
                                            totalSessionBlockedAds++
                                        }
                                    )

                                    webChromeClient = object : WebChromeClient() {
                                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                            currentTab.progress = newProgress
                                            currentTab.isLoading = newProgress < 100
                                        }

                                        override fun onReceivedTitle(view: WebView?, title: String?) {
                                            if (!title.isNullOrBlank()) {
                                                currentTab.title = title
                                            }
                                        }
                                    }

                                    loadUrl(currentTab.url)
                                }
                            }
                        },
                        update = { webView ->
                            if (webView.url != currentTab.url && currentTab.url != "helios://start") {
                                webView.loadUrl(currentTab.url)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Floating Bottom Address Bar with Gesture Support
            AddressBar(
                currentTab = currentTab,
                tabsCount = tabs.count { it.isIncognito == isPrivateMode },
                onNavigate = { query ->
                    val targetUrl = selectedSearchEngine.buildQueryUrl(query)
                    currentTab.url = targetUrl
                    webViewMap[currentTab.id]?.loadUrl(targetUrl)
                },
                onReload = {
                    webViewMap[currentTab.id]?.reload()
                },
                onOpenShields = { showShieldsSheet = true },
                onOpenTabs = { showTabSwitcher = true },
                onOpenMenu = { showMenuSheet = true },
                onSwipeNextTab = {
                    val currentModeTabs = tabs.filter { it.isIncognito == isPrivateMode }
                    val currentIndex = currentModeTabs.indexOfFirst { it.id == currentTabId }
                    if (currentIndex != -1 && currentIndex < currentModeTabs.size - 1) {
                        currentTabId = currentModeTabs[currentIndex + 1].id
                    }
                },
                onSwipePreviousTab = {
                    val currentModeTabs = tabs.filter { it.isIncognito == isPrivateMode }
                    val currentIndex = currentModeTabs.indexOfFirst { it.id == currentTabId }
                    if (currentIndex > 0) {
                        currentTabId = currentModeTabs[currentIndex - 1].id
                    }
                }
            )
        }

        // Shields Bottom Sheet
        if (showShieldsSheet) {
            ShieldsSheet(
                currentTab = currentTab,
                onDismiss = { showShieldsSheet = false },
                onToggleDesktopMode = { isDesktop ->
                    val webView = webViewMap[currentTab.id]
                    if (webView != null) {
                        webView.settings.userAgentString = if (isDesktop) {
                            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
                        } else {
                            null
                        }
                        webView.reload()
                    }
                }
            )
        }

        // Browser Menu Sheet
        if (showMenuSheet) {
            BrowserMenuSheet(
                isDesktopMode = currentTab.isDesktopMode,
                onToggleDesktop = {
                    currentTab.isDesktopMode = !currentTab.isDesktopMode
                    val webView = webViewMap[currentTab.id]
                    if (webView != null) {
                        webView.settings.userAgentString = if (currentTab.isDesktopMode) {
                            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
                        } else {
                            null
                        }
                        webView.reload()
                    }
                },
                onShare = { /* Share action */ },
                onNewTab = {
                    val newTab = TabModel(isIncognito = isPrivateMode)
                    tabs.add(newTab)
                    currentTabId = newTab.id
                },
                onNewPrivateTab = {
                    isPrivateMode = true
                    val newTab = TabModel(isIncognito = true)
                    tabs.add(newTab)
                    currentTabId = newTab.id
                },
                onDismiss = { showMenuSheet = false }
            )
        }

        // Tab Switcher Overlay
        AnimatedVisibility(
            visible = showTabSwitcher,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
        ) {
            TabSwitcher(
                tabs = tabs,
                currentTabId = currentTabId,
                isPrivateMode = isPrivateMode,
                onSelectTab = { selectedId ->
                    currentTabId = selectedId
                    showTabSwitcher = false
                },
                onCloseTab = { closedId ->
                    val index = tabs.indexOfFirst { it.id == closedId }
                    if (index != -1) {
                        webViewMap.remove(closedId)?.destroy()
                        tabs.removeAt(index)
                        if (tabs.isEmpty()) {
                            val freshTab = TabModel(isIncognito = isPrivateMode)
                            tabs.add(freshTab)
                            currentTabId = freshTab.id
                        } else if (currentTabId == closedId) {
                            val newIndex = (index - 1).coerceAtLeast(0)
                            currentTabId = tabs[newIndex].id
                        }
                    }
                },
                onNewTab = { privateMode ->
                    val newTab = TabModel(isIncognito = privateMode)
                    tabs.add(newTab)
                    currentTabId = newTab.id
                    showTabSwitcher = false
                },
                onCloseAllTabs = {
                    tabs.filter { it.isIncognito == isPrivateMode }.forEach {
                        webViewMap.remove(it.id)?.destroy()
                    }
                    tabs.removeAll { it.isIncognito == isPrivateMode }
                    val freshTab = TabModel(isIncognito = isPrivateMode)
                    tabs.add(freshTab)
                    currentTabId = freshTab.id
                },
                onTogglePrivateMode = { privateMode ->
                    isPrivateMode = privateMode
                },
                onCloseSwitcher = { showTabSwitcher = false }
            )
        }
    }
}

@Composable
private fun StartPageView(
    totalBlocked: Int,
    selectedSearchEngine: SearchEngine,
    onSelectSearchEngine: (SearchEngine) -> Unit,
    onSelectShortcut: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 28.dp, bottom = 100.dp)
    ) {
        // Welcome Hero Card
        item {
            WelcomeCard(totalBlockedAds = totalBlocked)
            Spacer(modifier = Modifier.height(24.dp))
        }

        // Search Engine Selector Chips
        item {
            Text(
                text = "Default Search Engine",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = HeliosTextSecondary,
                modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(SearchEngine.values()) { engine ->
                    val isSelected = engine == selectedSearchEngine
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSelected) HeliosBlue.copy(alpha = 0.2f) else HeliosDarkCard)
                            .border(
                                width = 1.dp,
                                color = if (isSelected) HeliosBlue else Color.White.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable { onSelectSearchEngine(engine) }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = engine.title,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) HeliosBlue else HeliosTextPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }

        // Speed Dial / Favorites Header
        item {
            Text(
                text = "Favorites & Quick Access",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = HeliosTextSecondary,
                modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
            )
        }

        // Speed Dial Items
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                DefaultShortcuts.items.take(3).forEach { shortcut ->
                    ShortcutTile(shortcut = shortcut, onClick = { onSelectShortcut(shortcut.url) })
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                DefaultShortcuts.items.drop(3).take(3).forEach { shortcut ->
                    ShortcutTile(shortcut = shortcut, onClick = { onSelectShortcut(shortcut.url) })
                }
            }
        }
    }
}

@Composable
private fun ShortcutTile(
    shortcut: com.helios.browser.data.BookmarkModel,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(104.dp)
            .height(84.dp)
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
                    .background(HeliosBlue.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = shortcut.initial,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = HeliosBlue
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = shortcut.title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = HeliosTextPrimary,
                maxLines = 1
            )
        }
    }
}
