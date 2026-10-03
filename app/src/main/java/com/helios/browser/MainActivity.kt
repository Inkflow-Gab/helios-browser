package com.helios.browser

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.helios.browser.domain.model.OmniboxPosition
import com.helios.browser.ui.adaptive.rememberHeliosLayout
import com.helios.browser.ui.browser.BrowserScreen
import com.helios.browser.ui.browser.BrowserViewModel
import com.helios.browser.ui.onboarding.OnboardingScreen
import com.helios.browser.ui.splash.SPLASH_MIN_VISIBLE_MILLIS
import com.helios.browser.ui.splash.SplashScreen
import com.helios.browser.ui.theme.HeliosOledBackground
import com.helios.browser.ui.theme.HeliosTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            HeliosTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = HeliosOledBackground
                ) {
                    HeliosApp()
                }
            }
        }
    }
}

/**
 * Chooses between the splash, first-run setup, and the browser.
 *
 * The gate is `AppSettings.onboardingCompleted` read from DataStore, which means it stays correct
 * across process death. Nothing else is composed until that first read resolves, otherwise the app
 * would flash the browser for a moment before setup appears.
 *
 * The splash is held for at least [SPLASH_MIN_VISIBLE_MILLIS] even on a warm DataStore read. That
 * floor is what turns a blank wait into a deliberate animation instead of a flicker.
 */
@Composable
private fun HeliosApp() {
    val browserViewModel: BrowserViewModel = hiltViewModel()
    val state by browserViewModel.state.collectAsStateWithLifecycle()

    val layout = rememberHeliosLayout(
        preferTopOmnibox = state.settings.omniboxPosition == OmniboxPosition.TOP
    )

    var splashElapsed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(SPLASH_MIN_VISIBLE_MILLIS)
        splashElapsed = true
    }

    Box(modifier = Modifier.fillMaxSize().background(HeliosOledBackground)) {
        when {
            !state.settingsLoaded -> Unit

            !state.settings.onboardingCompleted -> OnboardingScreen(
                onFinished = { /* DataStore flips the flag and this branch swaps itself out. */ },
                layout = layout
            )

            else -> BrowserScreen(viewModel = browserViewModel, layout = layout)
        }

        // Drawn last so it sits above whichever branch is showing, and fades off on top of it
        // rather than the content fading in over a black gap.
        SplashScreen(visible = !state.settingsLoaded || !splashElapsed)
    }
}