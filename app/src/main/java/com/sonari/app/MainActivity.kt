package com.sonari.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sonari.app.a11y.Announcer
import com.sonari.app.audio.Sonifier
import com.sonari.app.haptic.Haptics
import com.sonari.app.model.LineChart
import com.sonari.app.model.Renderable
import com.sonari.app.ui.ExplorerScreen
import com.sonari.app.ui.HomeScreen

// Shared between HomeScreen and ExplorerScreen via the activity's viewModelStore.
class AppViewModel : ViewModel() {
    var renderable: Renderable? = null
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

        setContent {
            MaterialTheme {
                Surface {
                    val navController = rememberNavController()
                    val vm: AppViewModel = viewModel()

                    NavHost(navController = navController, startDestination = "home") {
                        composable("home") {
                            HomeScreen(
                                onLoad = { chart ->
                                    vm.renderable = chart
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
                                    announcer = announcer
                                )
                            }
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
