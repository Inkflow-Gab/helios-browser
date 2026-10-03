package com.helios.browser.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helios.browser.domain.model.HistoryEntry
import com.helios.browser.ui.browser.BrowserIntent
import com.helios.browser.ui.icons.HeliosGlyphs
import com.helios.browser.ui.icons.HeliosIcons
import com.helios.browser.ui.theme.HeliosBlue
import com.helios.browser.ui.theme.HeliosDarkCard
import com.helios.browser.ui.theme.HeliosDarkSurface
import com.helios.browser.ui.theme.HeliosTextPrimary
import com.helios.browser.ui.theme.HeliosTextSecondary
import com.helios.browser.ui.theme.HeliosTextTertiary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Recently visited pages.
 *
 * Only populated when history is enabled in settings, and never for incognito tabs, so an empty
 * list here is expected in private mode.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistorySheet(
    entries: List<HistoryEntry>,
    onIntent: (BrowserIntent) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = HeliosDarkSurface,
        dragHandle = { SheetHandle() }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "History",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = HeliosTextPrimary
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = when (entries.size) {
                            0 -> "Nothing recorded yet"
                            1 -> "1 page"
                            else -> "${entries.size} pages"
                        },
                        fontSize = 12.sp,
                        color = HeliosTextSecondary
                    )
                }
                if (entries.isNotEmpty()) {
                    TextButton(onClick = { onIntent(BrowserIntent.ClearHistory) }) {
                        Text(
                            text = "Clear all",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = HeliosBlue
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (entries.isEmpty()) {
                EmptyState(
                    icon = HeliosGlyphs.History,
                    title = "No history",
                    body = "Pages you visit appear here unless history is turned off or you are " +
                        "in a private tab."
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(entries, key = { it.id }) { entry ->
                        HistoryRow(
                            entry = entry,
                            onOpen = { onIntent(BrowserIntent.OpenHistoryEntry(entry.url)) },
                            onDelete = { onIntent(BrowserIntent.DeleteHistoryEntry(entry.id)) }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@Composable
private fun HistoryRow(
    entry: HistoryEntry,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(HeliosDarkCard)
            .clickable(onClick = onOpen)
            .padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(HeliosBlue.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = entry.host.take(1).uppercase(),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = HeliosBlue
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = HeliosTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = entry.url,
                fontSize = 11.sp,
                color = HeliosTextTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = relativeTime(entry.visitedAt),
            fontSize = 10.sp,
            color = HeliosTextTertiary,
            modifier = Modifier.padding(horizontal = 6.dp)
        )
        IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
            Icon(
                imageVector = HeliosIcons.Trash,
                contentDescription = "Delete history entry",
                tint = HeliosTextSecondary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/** Compact relative timestamp. Falls back to a date once it is more than a week old. */
private fun relativeTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val delta = now - timestamp
    val minute = 60_000L
    val hour = 60 * minute
    val day = 24 * hour

    return when {
        delta < minute -> "now"
        delta < hour -> "${delta / minute}m"
        delta < day -> "${delta / hour}h"
        delta < 7 * day -> "${delta / day}d"
        else -> SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(timestamp))
    }
}