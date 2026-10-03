package com.helios.browser.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helios.browser.diagnostics.CrashReporter
import com.helios.browser.ui.theme.HeliosDarkCard
import com.helios.browser.ui.theme.HeliosOledBackground
import com.helios.browser.ui.theme.HeliosShieldRed
import com.helios.browser.ui.theme.HeliosTextPrimary
import com.helios.browser.ui.theme.HeliosTextSecondary
import com.helios.browser.ui.theme.HeliosTextTertiary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Shows any crash recorded on the previous run.
 *
 * ## Why this is worth the code
 * Helios is installed from a CI-built APK onto a phone with no debugger attached. Before this, a
 * crash produced the system dialog and nothing else — no way to know which of several hundred files
 * was at fault. Putting the trace on screen turns "it crashes" into something that can be read,
 * copied and sent.
 *
 * Deliberately *not* a modal that blocks launch. A beta browser that refuses to start because its
 * last run died is worse than one that starts and tells you about it. So this is offered as a banner
 * above the normal content and can be dismissed; if it is dismissed, the trace is still on disk and
 * `CrashReporter.recordedCrashes` still finds it.
 *
 * The trace is read on [Dispatchers.IO] and only the first ~4KB is shown: a deep stack can be tens of
 * kilobytes and the useful part is always at the top.
 */
@Composable
fun CrashBanner(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var report by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(context) {
        val first = withContext(Dispatchers.IO) { CrashReporter.recordedCrashes(context).firstOrNull() }
        if (first != null) {
            report = withContext(Dispatchers.IO) { CrashReporter.readCrash(context, first) }
        }
    }

    val text = report ?: return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(HeliosDarkCard)
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Helios crashed last time",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = HeliosShieldRed
            )
            TextButton(onClick = {
                scope.launch {
                    withContext(Dispatchers.IO) { CrashReporter.clear(context) }
                    report = null
                    onDismiss()
                }
            }) {
                Text(text = "Dismiss", fontSize = 12.sp, color = HeliosTextTertiary)
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "This is what went wrong. Keep it if you are reporting the crash.",
            fontSize = 11.sp,
            lineHeight = 16.sp,
            color = HeliosTextSecondary
        )
        Spacer(modifier = Modifier.height(10.dp))
        CrashTraceText(text)
    }
}

/**
 * The trace, monospaced, clipped to a readable height.
 *
 * Horizontal scrolling is deliberate: stack frames are long and wrapping them destroys the
 * indentation that makes a trace readable. The height cap keeps the banner from swallowing the
 * browser on a phone.
 */
@Composable
private fun CrashTraceText(text: String) {
    val clipped = remember(text) { text.take(MAX_TRACE_CHARS) }
    val truncated = remember(text) { text.length > MAX_TRACE_CHARS }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(HeliosOledBackground)
            .padding(10.dp)
    ) {
        Text(
            text = clipped,
            fontSize = 10.sp,
            lineHeight = 13.sp,
            fontFamily = FontFamily.Monospace,
            color = HeliosTextPrimary,
            modifier = Modifier
                .fillMaxWidth()
                .height(TRACE_HEIGHT_DP.dp)
                .horizontalScroll(rememberScrollState())
                .verticalScroll(rememberScrollState())
        )
        if (truncated) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Trace truncated at $MAX_TRACE_CHARS characters. The full one is on disk.",
                fontSize = 10.sp,
                color = HeliosTextTertiary
            )
        }
    }
}

private const val MAX_TRACE_CHARS = 4_000
private const val TRACE_HEIGHT_DP = 180