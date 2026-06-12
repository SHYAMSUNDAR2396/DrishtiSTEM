package com.technoblaze.drishtistem

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.technoblaze.drishtistem.core.Engines
import com.technoblaze.drishtistem.data.ConceptRepository
import com.technoblaze.drishtistem.model.GraphConcept
import com.technoblaze.drishtistem.model.MoleculeConcept
import com.technoblaze.drishtistem.model.Subject
import com.technoblaze.drishtistem.model.WaveConcept
import com.technoblaze.drishtistem.ui.graph.GraphExplorerScreen
import com.technoblaze.drishtistem.ui.home.HomeScreen
import com.technoblaze.drishtistem.ui.home.SubjectScreen
import com.technoblaze.drishtistem.ui.molecule.MoleculeScreen
import com.technoblaze.drishtistem.ui.wave.WaveLabScreen

class MainActivity : ComponentActivity() {

    private lateinit var engines: Engines

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        engines = Engines(this)
        engines.start()
        // Exploration sessions are long and touch-driven; never sleep mid-trace.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            DrishtiTheme {
                DrishtiNavHost(engines)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        engines.quietAll()
    }

    override fun onDestroy() {
        super.onDestroy()
        engines.release()
    }
}

@Composable
private fun DrishtiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFFB5651D),
            background = Color(0xFF1A1B1E),
            surface = Color(0xFF2E3136)
        ),
        content = content
    )
}

@Composable
private fun DrishtiNavHost(engines: Engines) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(engines) { subject ->
                navController.navigate("subject/${subject.name}")
            }
        }
        composable(
            "subject/{name}",
            arguments = listOf(navArgument("name") { type = NavType.StringType })
        ) { entry ->
            val subject = Subject.valueOf(entry.arguments?.getString("name") ?: Subject.MATHS.name)
            SubjectScreen(
                subject = subject,
                engines = engines,
                onConcept = { id -> navController.navigate("concept/$id") },
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            "concept/{id}",
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) { entry ->
            val id = entry.arguments?.getString("id")
            val onBack: () -> Unit = { navController.popBackStack() }
            when (val concept = id?.let(ConceptRepository::byId)) {
                is GraphConcept -> GraphExplorerScreen(concept, engines, onBack)
                is WaveConcept -> WaveLabScreen(concept, engines, onBack)
                is MoleculeConcept -> MoleculeScreen(concept, engines, onBack)
                null -> onBack()
            }
        }
    }
}
