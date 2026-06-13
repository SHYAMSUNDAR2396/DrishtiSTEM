package com.sonari.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sonari.app.a11y.Announcer
import com.sonari.app.audio.Sonifier
import com.sonari.app.haptic.Haptics
import com.sonari.app.model.Renderable
import com.sonari.app.ui.ExplorerScreen
import com.sonari.app.ui.HomeScreen
import com.sonari.app.ui.SettingsScreen
import com.sonari.app.ui.SonariSettings
import com.sonari.app.ui.TutorialScreen

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

                    NavHost(
                        navController = navController,
                        startDestination = if (tutorialDone) "home" else "tutorial"
                    ) {
                        composable("tutorial") {
                            TutorialScreen(
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
                                }
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
                                onSettingsChange = { vm.settings = it }
                            )
                        }
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
