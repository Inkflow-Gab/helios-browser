package com.helios.browser.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helios.browser.domain.model.BrowserTab
import com.helios.browser.engine.CookieEntry
import com.helios.browser.engine.CookieTools
import com.helios.browser.ui.icons.HeliosGlyphs
import com.helios.browser.ui.icons.HeliosIcons
import com.helios.browser.ui.theme.HeliosDarkCard
import com.helios.browser.ui.theme.HeliosDarkSurface
import com.helios.browser.ui.theme.HeliosShieldGreen
import com.helios.browser.ui.theme.HeliosShieldOrange
import com.helios.browser.ui.theme.HeliosShieldRed
import com.helios.browser.ui.theme.HeliosSun
import com.helios.browser.ui.theme.HeliosTextPrimary
import com.helios.browser.ui.theme.HeliosTextSecondary
import com.helios.browser.ui.theme.HeliosTextTertiary
import kotlinx.coroutines.launch

/**
 * Cookie tools: paste a jar, add one by hand, copy this site's cookies out, clear them.
 *
 * ## Why this exists
 * Sessions expire, sites invalidate tokens for no stated reason, and the fix is always the same
 * tedious dance: sign in again, or find the cookie, copy it, and paste it somewhere. On a phone
 * that dance is genuinely hard, and there is no system tool for it.
 *
 * ## What it does not pretend
 * - A jar is only applied when every cookie in it belongs to the open page. A mixed jar is refused
 *   outright rather than written against whichever site happens to be open, because that sets one
 *   site's session token on another.
 * - Rejected lines are listed. A paste that silently drops three lines reads as "it worked" and
 *   fails later as an unexplained logout.
 * - **Copy** produces names and values that are exactly what the store holds, but the domain is the
 *   host asked about and the path, secure flag and expiry are reconstructed defaults. `CookieManager`
 *   exposes no way to read a cookie's attributes. Saying so is better than handing over a jar that
 *   looks authoritative and is partly invented.
 * - In a private tab nothing here works, because cookies are switched off for that tab and a write
 *   would be silently dropped. That is said in words rather than shown as a button that does
 *   nothing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CookieSheet(
    tab: BrowserTab?,
    onReload: () -> Unit,
    onApplied: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    // CookieManager is a process singleton, so this needs no Context and no lifecycle. Held in
    // `remember` because constructing it per recomposition would be pointless and the lint for it
    // is noise rather than a real defect.
    val tools = remember { CookieTools() }

    val page = tab?.takeIf { !it.isStartPage }
    val isPrivate = page?.isIncognito == true
    val pageUrl = page?.url

    var mode by remember { mutableStateOf(Mode.JAR) }
    var jar by remember { mutableStateOf("") }
    var cookieName by remember { mutableStateOf("") }
    var cookieValue by remember { mutableStateOf("") }
    var cookieSecure by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<Report?>(null) }
    var busy by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = HeliosDarkSurface,
        dragHandle = { SheetHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Cookies",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = HeliosTextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = when {
                            page == null -> "No page open"
                            isPrivate -> "Private tab — cookies are off here"
                            else -> page.displayHost
                        },
                        fontSize = 12.sp,
                        color = if (isPrivate) HeliosShieldOrange else HeliosTextSecondary
                    )
                }
                Icon(
                    imageVector = HeliosIcons.Close,
                    contentDescription = "Close",
                    tint = HeliosTextTertiary,
                    modifier = Modifier
                        .size(20.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onDismiss)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))

            if (page == null) {
                EmptyState(
                    icon = HeliosGlyphs.Globe,
                    title = "Open a page first",
                    body = "Cookies always belong to a site, so there is nothing to work on until " +
                        "a page is loaded."
                )
                Spacer(modifier = Modifier.height(28.dp))
                return@Column
            }

            if (isPrivate) {
                EmptyState(
                    icon = HeliosIcons.Incognito,
                    title = "Nothing to do in a private tab",
                    body = "Cookies are switched off for private tabs. Anything written here would " +
                        "be dropped without an error, which is why these tools are closed off " +
                        "rather than silently ineffective."
                )
                Spacer(modifier = Modifier.height(28.dp))
                return@Column
            }

            // --- two modes, because pasting 40 lines to fix one cookie is absurd ----------------
            ModeToggle(
                selected = mode,
                onSelect = {
                    mode = it
                    report = null
                }
            )
            Spacer(modifier = Modifier.height(14.dp))

            if (mode == Mode.JAR) {
                DarkField(
                    value = jar,
                    onValueChange = { jar = it },
                    placeholder = "Paste a cookie jar (Netscape format)",
                    singleLine = false,
                    minLines = 4,
                    monospace = true
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionButton(
                        label = "Paste",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            clipboard.getText()?.text?.let {
                                jar = it
                                report = null
                            }
                        }
                    ) {
                        Text(
                            text = "Read the clipboard",
                            fontSize = 12.sp,
                            color = HeliosTextSecondary
                        )
                    }
                    ActionButton(
                        label = if (busy) "Working…" else "Apply",
                        primary = true,
                        enabled = jar.isNotBlank() && !busy,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            busy = true
                            report = null
                            tools.apply(jar, pageUrl, isPrivate) { result ->
                                // Marshalled to main: setCookie's callback thread is not specified,
                                // and writing Compose state from another thread throws.
                                scope.launch {
                                    busy = false
                                    report = result.toReport()
                                    // Reload, so the cookies are actually sent rather than merely
                                    // written. This is the whole point of the tool: a cookie sitting
                                    // in the store changes nothing until a request carries it, and
                                    // the page in front of the user has already made its requests.
                                    // Without this the sheet says "reload the page" and the user
                                    // reads that as the tool not having worked.
                                    if (result.applied == 0) return@launch
                                    if (result.rejectedLines.isEmpty()) {
                                        // All good: get out of the way and show the site.
                                        onApplied(
                                            "Applied ${result.applied} " +
                                                "${if (result.applied == 1) "cookie" else "cookies"} " +
                                                "to ${page.displayHost}"
                                        )
                                    } else {
                                        // Some lines failed. Reload, but stay open: the rejected
                                        // lines are the part the user needs to read and fix.
                                        onReload()
                                    }
                                }
                            }
                        }
                    )
                }
            } else {
                DarkField(
                    value = cookieName,
                    onValueChange = { cookieName = it },
                    placeholder = "Name",
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                DarkField(
                    value = cookieValue,
                    onValueChange = { cookieValue = it },
                    placeholder = "Value",
                    singleLine = true,
                    monospace = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Secure only",
                        fontSize = 13.sp,
                        color = HeliosTextSecondary,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = cookieSecure,
                        onCheckedChange = { cookieSecure = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = HeliosSun,
                            checkedTrackColor = HeliosSun.copy(alpha = 0.3f),
                            uncheckedThumbColor = HeliosTextTertiary,
                            uncheckedTrackColor = Color.White.copy(alpha = 0.08f),
                            uncheckedBorderColor = Color.White.copy(alpha = 0.16f)
                        )
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                ActionButton(
                    label = if (busy) "Working…" else "Add cookie",
                    primary = true,
                    enabled = cookieName.isNotBlank() && !busy,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val host = page.displayHost
                        val entry = CookieEntry(
                            domain = host,
                            secure = cookieSecure,
                            name = cookieName.trim(),
                            value = cookieValue
                        )
                        busy = true
                        report = null
                        tools.addSingle(entry, pageUrl, isPrivate) { ok ->
                            scope.launch {
                                busy = false
                                if (ok) {
                                    // Straight to the site. Nothing to read here, and onApplied
                                    // closes the sheet, so no report either.
                                    cookieName = ""
                                    cookieValue = ""
                                    onApplied("Added ${entry.name} to $host")
                                } else {
                                    // This one keeps the sheet open, because the user has to see why.
                                    report = Report(
                                        title = "Could not set that cookie",
                                        detail = "The name or value contains something a cookie " +
                                            "header cannot carry, or the store refused the write.",
                                        tone = Tone.BAD
                                    )
                                }
                            }
                        }
                    }
                )
            }

            // --- outcome --------------------------------------------------------------------------
            report?.let {
                Spacer(modifier = Modifier.height(14.dp))
                ReportCard(it)
            }

            // --- read and clear -------------------------------------------------------------------
            Spacer(modifier = Modifier.height(20.dp))
            Rule()
            Spacer(modifier = Modifier.height(14.dp))
            SectionLabel("This site")
            Spacer(modifier = Modifier.height(10.dp))

            ActionButton(
                label = "Copy this site's cookies",
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val jar = tools.exportFor(pageUrl ?: return@ActionButton)
                    if (jar.isBlank()) {
                        report = Report(
                            title = "No cookies stored for ${page.displayHost}",
                            detail = "Nothing to copy.",
                            tone = Tone.WARN
                        )
                    } else {
                        clipboard.setText(androidx.compose.ui.text.AnnotatedString(jar))
                        val count = jar.lines().count { it.isNotBlank() }
                        report = Report(
                            title = "Copied $count cookies",
                            detail = "Names and values are exactly what is stored. The domain, " +
                                "path, secure flag and expiry are reconstructed, because " +
                                "CookieManager does not expose a cookie's attributes.",
                            tone = Tone.WARN
                        )
                    }
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            ActionButton(
                label = "Clear this site's cookies",
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    tools.clearFor(pageUrl ?: return@ActionButton) { cleared ->
                        scope.launch {
                            report = Report(
                                title = "Cleared $cleared cookies",
                                detail = if (cleared == 0) {
                                    "The store held nothing that could be deleted for this URL."
                                } else {
                                    "You will likely be signed out of this site."
                                },
                                tone = if (cleared == 0) Tone.WARN else Tone.GOOD
                            )
                        }
                    }
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            ActionButton(
                label = "Clear every cookie",
                destructive = true,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    tools.removeAll { ok ->
                        scope.launch {
                            report = Report(
                                title = if (ok) "Cleared all cookies" else "Could not clear all cookies",
                                detail = if (ok) {
                                    "Every site in Helios is signed out."
                                } else {
                                    "The store refused the bulk delete."
                                },
                                tone = if (ok) Tone.GOOD else Tone.BAD
                            )
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

private enum class Mode { JAR, SINGLE }

/** The outcome of an action, shown in words rather than only as a count. */
private data class Report(
    val title: String,
    val detail: String,
    val tone: Tone,
    val rejected: List<String> = emptyList()
)

private enum class Tone { GOOD, WARN, BAD }

/**
 * Turns a raw [CookieTools.ApplyResult] into something a person can act on.
 *
 * Every refusal gets its own sentence. The alternative was one generic "could not apply", which
 * leaves the user with a paste that did nothing and no idea whether to open a page, pick the right
 * site, or fix the text.
 */
private fun CookieTools.ApplyResult.toReport(): Report = when (refusal) {
    CookieTools.Refusal.PRIVATE_MODE -> Report(
        title = "Private tabs cannot hold cookies",
        detail = "Cookies are switched off here, so a paste would be dropped without an error.",
        tone = Tone.BAD
    )

    CookieTools.Refusal.NOT_A_PAGE -> Report(
        title = "No page to scope this to",
        detail = "Cookies belong to a site. Open the site these cookies are for and try again.",
        tone = Tone.BAD
    )

    CookieTools.Refusal.HOST_MISMATCH -> Report(
        title = "Those cookies are for a different site",
        detail = "Helios only writes cookies for the page in front of it, so a jar from another " +
            "site is refused. Open that site and paste it there.",
        tone = Tone.WARN
    )

    CookieTools.Refusal.MIXED_HOSTS -> Report(
        title = "That jar covers more than one site",
        detail = "It has cookies for several domains, so there is no single page to apply them " +
            "to. Split it, or open each site and paste its own lines.",
        tone = Tone.WARN
    )

    CookieTools.Refusal.NOTHING_PARSED -> Report(
        title = "Nothing readable in that",
        detail = "No line had the seven columns a cookie line needs. A jar looks like " +
            "\"site.com\\tFALSE\\t/\\tTRUE\\t0\\tname\\tvalue\", tabs between them.",
        tone = Tone.BAD,
        rejected = rejectedLines
    )

    null -> {
        val total = applied + removed
        Report(
            title = when {
                total == 0 -> "Nothing was applied"
                else -> "Applied $total ${if (total == 1) "cookie" else "cookies"}"
            },
            detail = when {
                removed > 0 && applied > 0 -> "$applied set, $removed deleted, because some lines " +
                    "in the jar had an expiry in the past."
                removed > 0 -> "All $removed lines were deletions: their expiry had already passed."
                else -> "Reload the page to pick them up."
            },
            tone = if (total == 0) Tone.BAD else Tone.GOOD,
            rejected = rejectedLines
        )
    }
}

@Composable
private fun ModeToggle(selected: Mode, onSelect: (Mode) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        ToggleHalf("Paste a jar", selected == Mode.JAR, Modifier.weight(1f)) { onSelect(Mode.JAR) }
        ToggleHalf("Add one", selected == Mode.SINGLE, Modifier.weight(1f)) { onSelect(Mode.SINGLE) }
    }
}

@Composable
private fun ToggleHalf(label: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) Color.White.copy(alpha = 0.11f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            color = if (active) HeliosTextPrimary else HeliosTextSecondary
        )
    }
}

/**
 * A single-line or few-line input styled like the rest of the app.
 *
 * `BasicTextField` with no decoration rather than `OutlinedTextField`, because the Material
 * component brings its own theming that does not match the glass surfaces and cannot be made to
 * without fighting it.
 */
@Composable
private fun DarkField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    singleLine: Boolean,
    minLines: Int = 1,
    monospace: Boolean = false
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        minLines = minLines,
        cursorBrush = SolidColor(HeliosSun),
        textStyle = TextStyle(
            color = HeliosTextPrimary,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(HeliosDarkCard)
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(13.dp))
            .padding(horizontal = 13.dp, vertical = 11.dp),
        decorationBox = { inner ->
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    fontSize = 13.sp,
                    color = HeliosTextTertiary
                )
            }
            inner()
        }
    )
}

@Composable
private fun ActionButton(
    label: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    destructive: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
    subtitle: (@Composable () -> Unit)? = null
) {
    val tint = when {
        destructive -> HeliosShieldRed
        primary -> HeliosSun
        else -> HeliosTextSecondary
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(13.dp))
            .background(
                when {
                    primary -> HeliosSun.copy(alpha = 0.16f)
                    destructive -> HeliosShieldRed.copy(alpha = 0.12f)
                    else -> HeliosDarkCard
                }
            )
            .border(
                1.dp,
                when {
                    primary -> HeliosSun.copy(alpha = 0.32f)
                    destructive -> HeliosShieldRed.copy(alpha = 0.28f)
                    else -> Color.White.copy(alpha = 0.06f)
                },
                RoundedCornerShape(13.dp)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = if (enabled) tint else HeliosTextTertiary
        )
        subtitle?.let {
            Spacer(modifier = Modifier.weight(1f))
            it()
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = HeliosTextTertiary
    )
}

@Composable
private fun Rule() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color.White.copy(alpha = 0.07f))
    )
}

/**
 * The outcome, in a card.
 *
 * Rejected lines are shown rather than counted. "2 lines could not be read" leaves the user with
 * nothing to fix; the line itself is the thing they need, so it is given to them, truncated so a
 * pathological paste cannot turn into a wall of text.
 */
@Composable
private fun ReportCard(report: Report) {
    val accent = when (report.tone) {
        Tone.GOOD -> HeliosShieldGreen
        Tone.WARN -> HeliosShieldOrange
        Tone.BAD -> HeliosShieldRed
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(HeliosDarkCard)
            .border(1.dp, accent.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Text(
            text = report.title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = accent
        )
        Spacer(modifier = Modifier.height(5.dp))
        Text(
            text = report.detail,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            color = HeliosTextSecondary
        )
        if (report.rejected.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Lines Helios could not read",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = HeliosTextTertiary
            )
            Spacer(modifier = Modifier.height(5.dp))
            report.rejected.take(6).forEach { line ->
                Text(
                    text = line,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    color = HeliosTextTertiary,
                    maxLines = 2,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }
            if (report.rejected.size > 6) {
                Text(
                    text = "and ${report.rejected.size - 6} more",
                    fontSize = 10.sp,
                    color = HeliosTextTertiary
                )
            }
        }
    }
}