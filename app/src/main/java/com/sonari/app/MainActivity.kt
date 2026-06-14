package com.sonari.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sonari.app.a11y.Announcer
import com.sonari.app.audio.Sonifier
import com.sonari.app.data.EquationLoader
import com.sonari.app.data.MoleculeLoader
import com.sonari.app.haptic.Haptics
import com.sonari.app.model.Renderable
import com.sonari.app.ui.ExplorerScreen
import com.sonari.app.ui.HomeScreen
import com.sonari.app.ui.SettingsScreen
import com.sonari.app.ui.SonariSettings
import com.sonari.app.ui.TutorialScreen
import com.sonari.app.voice.GemmaVoiceBrain
import com.sonari.app.voice.VoiceButton
import com.sonari.app.voice.VoiceButtonState
import com.sonari.app.voice.VoiceCommand
import com.sonari.app.voice.parseVoiceCommand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppViewModel : ViewModel() {
    var renderable: Renderable? = null
    var settings: SonariSettings = SonariSettings()
}

class MainActivity : ComponentActivity() {

    private lateinit var sonifier: Sonifier
    private lateinit var haptics: Haptics
    private lateinit var announcer: Announcer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sonifier = Sonifier()
        haptics = Haptics.from(this)
        announcer = Announcer(this)

        val prefs = getSharedPreferences("sonari", Context.MODE_PRIVATE)
        val tutorialDone = prefs.getBoolean("tutorial_done", false)

        setContent {
            MaterialTheme {
                Surface {
                    val navController = rememberNavController()
                    val vm: AppViewModel = viewModel()
                    val scope = rememberCoroutineScope()
                    val context = LocalContext.current.applicationContext

                    val gemmaBrain = remember { GemmaVoiceBrain(context) }
                    var brainReady by remember { mutableStateOf(false) }
                    var voiceState by remember { mutableStateOf(VoiceButtonState.IDLE) }

                    LaunchedEffect(Unit) {
                        brainReady = gemmaBrain.ensureLoaded()
                    }

                    DisposableEffect(Unit) {
                        onDispose { gemmaBrain.close() }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        NavHost(
                            navController = navController,
                            startDestination = if (tutorialDone) "home" else "tutorial"
                        ) {
                            composable("tutorial") {
                                TutorialScreen(
                                    announcer = announcer,
                                    sonifier = sonifier,
                                    haptics = haptics,
                                    onFinish = { renderable ->
                                        prefs.edit().putBoolean("tutorial_done", true).apply()
                                        vm.renderable = renderable
                                        navController.navigate("explore") {
                                            popUpTo("tutorial") { inclusive = true }
                                        }
                                    },
                                    onDismiss = {
                                        prefs.edit().putBoolean("tutorial_done", true).apply()
                                        navController.navigate("home") {
                                            popUpTo("tutorial") { inclusive = true }
                                        }
                                    }
                                )
                            }
                            composable("home") {
                                HomeScreen(
                                    onLoad = { renderable ->
                                        vm.renderable = renderable
                                        navController.navigate("explore")
                                    },
                                    onTutorial = { navController.navigate("tutorial") }
                                )
                            }
                            composable("explore") {
                                val r = vm.renderable
                                if (r != null) {
                                    ExplorerScreen(
                                        renderable = r,
                                        sonifier = sonifier,
                                        haptics = haptics,
                                        announcer = announcer,
                                        onNavigateHome = { navController.navigate("home") },
                                        onNavigateSettings = { navController.navigate("settings") }
                                    )
                                }
                            }
                            composable("settings") {
                                SettingsScreen(
                                    settings = vm.settings,
                                    onSettingsChange = { vm.settings = it },
                                    onTutorial = { navController.navigate("tutorial") }
                                )
                            }
                        }

                        // ── Global voice command overlay ────────────────────────────
                        VoiceButton(
                            buttonState = voiceState,
                            onResult = { text ->
                                scope.launch {
                                    voiceState = VoiceButtonState.PROCESSING
                                    val cmd = if (brainReady) {
                                        gemmaBrain.parse(text) ?: parseVoiceCommand(text)
                                    } else {
                                        parseVoiceCommand(text)
                                    }
                                    voiceState = VoiceButtonState.IDLE
                                    when (cmd) {
                                    is VoiceCommand.LoadMolecule -> {
                                        scope.launch {
                                            val mol = withContext(Dispatchers.IO) {
                                                MoleculeLoader.load(cmd.name).getOrNull()
                                            }
                                            if (mol != null) {
                                                vm.renderable = mol
                                                navController.navigate("explore") {
                                                    launchSingleTop = true
                                                }
                                            }
                                        }
                                    }
                                    is VoiceCommand.LoadEquation -> {
                                        EquationLoader.load(cmd.expr).onSuccess { chart ->
                                            vm.renderable = chart
                                            navController.navigate("explore") {
                                                launchSingleTop = true
                                            }
                                        }
                                    }
                                    is VoiceCommand.NavigateBack -> navController.popBackStack()
                                    is VoiceCommand.NavigateHome -> {
                                        navController.navigate("home") {
                                            popUpTo("home") { inclusive = true }
                                        }
                                    }
                                    is VoiceCommand.OpenTutorial -> {
                                        navController.navigate("tutorial") {
                                            launchSingleTop = true
                                        }
                                    }
                                    is VoiceCommand.OpenSettings -> {
                                        navController.navigate("settings") {
                                            launchSingleTop = true
                                        }
                                    }
                                    null -> { /* unrecognised — silent */ }
                                }
                            }
                        },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 24.dp)
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sonifier.release()
        announcer.release()
    }
}
