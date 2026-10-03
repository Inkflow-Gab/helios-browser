package com.helios.browser.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helios.browser.BuildConfig
import com.helios.browser.domain.model.HeliosCredits
import com.helios.browser.ui.icons.HeliosGlyphs
import com.helios.browser.ui.theme.HeliosDarkCard
import com.helios.browser.ui.theme.HeliosDarkSurface
import com.helios.browser.ui.theme.HeliosShieldGreen
import com.helios.browser.ui.theme.HeliosSun
import com.helios.browser.ui.theme.HeliosTextPrimary
import com.helios.browser.ui.theme.HeliosTextSecondary
import com.helios.browser.ui.theme.HeliosTextTertiary

/**
 * Attribution for everything Helios is built out of.
 *
 * ## Why this is a sheet and not a line in the menu
 * Every entry is a licence obligation rather than a courtesy. MPL-2.0, GPL-3.0 and the SIL OFL all
 * require that the terms reach the person receiving the binary, and BSD-3 and Apache-2.0 require
 * their notices to be retained. Burying that behind one "About" row would satisfy nobody: the whole
 * point is that it is reachable in a tap or two, and that the full licence text is actually in the
 * app rather than implied by a badge.
 *
 * Tapping a credit with a bundled licence opens the real text, read from `assets/licenses`. Tapping
 * a credit with a project URL offers to open it.
 *
 * @param onOpenUrl invoked with an https URL. Handed to the ViewModel rather than opening here, so
 *   no `Context` is captured and the sheet stays presentable from anywhere.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreditsSheet(
    onOpenUrl: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var reading by remember { mutableStateOf<HeliosCredits.Credit?>(null) }

    // The licence viewer replaces the credits list rather than stacking on top of it. Two nested
    // sheets on a low-end phone is two dimmed scrims and a lot of jank for something read once.
    val viewing = reading
    if (viewing != null) {
        LicenceTextSheet(
            credit = viewing,
            onOpenUrl = onOpenUrl,
            onBack = { reading = null },
            onDismiss = onDismiss
        )
        return
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = HeliosDarkSurface,
        dragHandle = { SheetHandle() }
    ) {
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            item(key = "header") { CreditsHeader() }

            HeliosCredits.grouped.forEach { (group, credits) ->
                item(key = "group-${group.name}") {
                    GroupHeader(group = group)
                }
                items(credits, key = { it.name }) { credit ->
                    CreditCard(credit = credit, onClick = { reading = credit })
                }
            }

            item(key = "footer") { CreditsFooter() }
        }
    }
}

@Composable
private fun CreditsHeader() {
    Column {
        Text(
            text = "Helios",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = HeliosTextPrimary
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = "Version " + BuildConfig.VERSION_NAME,
            fontSize = 12.sp,
            color = HeliosTextSecondary
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Helios is assembled from other people's work. This is who. Every licence here " +
                "is required: the copyleft ones demand their terms travel with the app, and the " +
                "permissive ones demand their notices are kept.",
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = HeliosTextSecondary
        )
        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
private fun GroupHeader(group: HeliosCredits.Group) {
    Column(modifier = Modifier.padding(top = 12.dp, bottom = 2.dp)) {
        Text(
            text = group.title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = HeliosSun
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = group.blurb,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            color = HeliosTextTertiary
        )
    }
}

@Composable
private fun CreditCard(credit: HeliosCredits.Credit, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(HeliosDarkCard)
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = credit.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = HeliosTextPrimary,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            LicenceBadge(licence = credit.licence)
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = credit.author,
            fontSize = 11.sp,
            color = HeliosTextTertiary
        )
        Spacer(modifier = Modifier.height(7.dp))
        Text(
            text = credit.role,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            color = HeliosTextSecondary
        )
        Spacer(modifier = Modifier.height(7.dp))
        // Tells the user what the tap does before they tap it, which matters because the two
        // outcomes are different: full text in a viewer, or the project page in the browser.
        Text(
            text = when {
                credit.licenceFile != null -> "Tap to read the full ${credit.licence} licence"
                credit.url != null -> "Tap to open the project page"
                else -> "No licence text bundled"
            },
            fontSize = 10.sp,
            color = if (credit.licenceFile != null) HeliosShieldGreen else HeliosTextTertiary
        )
    }
}

/**
 * A licence identifier, coloured by how much it constrains us.
 *
 * The colour is not decoration. Copyleft is the category that changes what we may do with Helios
 * itself, so it is worth being able to see at a glance which of these are copyleft.
 */
@Composable
private fun LicenceBadge(licence: String) {
    val copyleft = licence.contains("GPL") || licence.contains("MPL") || licence.contains("OFL")
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(if (copyleft) HeliosSun.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.06f))
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(
            text = licence,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            color = if (copyleft) HeliosSun else HeliosTextSecondary
        )
    }
}

@Composable
private fun CreditsFooter() {
    Column(modifier = Modifier.padding(top = 20.dp)) {
        Text(
            text = "What Helios wrote itself",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = HeliosTextPrimary
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "The interface, the settings, bookmarks, history, tabs, downloads, the cookie " +
                "tools, the JNI shim over adblock-rust, the filter list updater, the crash handler " +
                "and the Helios mark. The engine that draws pages is not ours and is not claimed " +
                "to be.",
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = HeliosTextSecondary
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "Source is public. The blocking engine is a separate Rust crate, and the " +
                "copyleft obligations above are met by keeping it a distinct work reached over a " +
                "JNI boundary rather than by linking its source into this one.",
            fontSize = 11.sp,
            lineHeight = 16.sp,
            color = HeliosTextTertiary
        )
    }
}

/**
 * The full licence text, read from `assets/licenses`.
 *
 * Read on a background dispatcher and held in state rather than read during composition, because
 * GPL-3.0 is 35 KB and reading a file on the main thread is exactly the kind of jank that gets
 * blamed on the animation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LicenceTextSheet(
    credit: HeliosCredits.Credit,
    onOpenUrl: (String) -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Keyed on the file so switching between two credits re-reads instead of showing the old text.
    val file = credit.licenceFile
    val text by produceState<String?>(initialValue = null, file) {
        value = if (file == null) null else readAsset(context, file)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = HeliosDarkSurface,
        dragHandle = { SheetHandle() }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Text(
                    text = credit.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = HeliosTextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = credit.licence + "  " + credit.author,
                    fontSize = 12.sp,
                    color = HeliosTextSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            when {
                text == null && file != null -> Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Reading licence…",
                        fontSize = 12.sp,
                        color = HeliosTextTertiary
                    )
                }

                text == null -> EmptyState(
                    icon = HeliosGlyphs.Info,
                    title = "No licence text",
                    body = "This component's terms are named above but the full text is not " +
                        "bundled. Follow the project link for the authoritative copy."
                )

                else -> Text(
                    text = text!!,
                    fontSize = 10.sp,
                    lineHeight = 15.sp,
                    fontFamily = FontFamily.Monospace,
                    color = HeliosTextSecondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ActionButton(label = "Back", onClick = onBack, modifier = Modifier.weight(1f))
                if (credit.url != null) {
                    ActionButton(
                        label = "Project page",
                        onClick = { onOpenUrl(credit.url) },
                        modifier = Modifier.weight(1f),
                        primary = true
                    )
                }
            }
            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@Composable
private fun ActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (primary) HeliosSun.copy(alpha = 0.16f) else HeliosDarkCard)
            .border(
                1.dp,
                if (primary) HeliosSun.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.06f),
                RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = if (primary) HeliosSun else HeliosTextSecondary
        )
    }
}

/**
 * Reads a bundled text asset, off the main thread, failing to null rather than throwing.
 *
 * A missing licence file must not be able to take the app down when someone taps Credits, which is
 * exactly when it would be least welcome. `runCatching` plus a null return means the sheet says the
 * text is unavailable instead.
 */
private suspend fun readAsset(context: android.content.Context, path: String): String? =
    runCatching {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            context.assets.open(path).bufferedReader().use { it.readText() }
        }
    }.getOrNull()