package com.helios.browser.data

import java.util.UUID

data class TabModel(
    val id: String = UUID.randomUUID().toString(),
    var url: String = "helios://start",
    var title: String = "Start Page",
    var isIncognito: Boolean = false,
    var isLoading: Boolean = false,
    var progress: Int = 0,
    var canGoBack: Boolean = false,
    var canGoForward: Boolean = false,
    var isDesktopMode: Boolean = false,
    var blockedAdsCount: Int = 0,
    var blockedTrackersCount: Int = 0
)
