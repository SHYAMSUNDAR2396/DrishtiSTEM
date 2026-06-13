package com.sonari.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.sonari.app.a11y.Announcer
import com.sonari.app.audio.Sonifier
import com.sonari.app.haptic.Haptics
import com.sonari.app.model.DataPoint
import com.sonari.app.model.Landmark
import com.sonari.app.model.LineChart
import com.sonari.app.ui.ExplorerScreen
import kotlin.math.pow

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
                    ExplorerScreen(
                        renderable = hardcodedParabola(),
                        sonifier = sonifier,
                        haptics = haptics,
                        announcer = announcer
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sonifier.release()
        announcer.release()
    }

    private fun hardcodedParabola(): LineChart {
        val xMin = -3.0
        val xMax = 3.0
        val yMin = 0.0
        val yMax = 9.0
        val samples = (-300..300).map { i ->
            val x = i / 100.0
            DataPoint(x, x.pow(2))
        }
        val landmarks = listOf(
            Landmark(0.5, 0.0, Landmark.Type.EXTREMUM, "vertex, x 0, y 0"),
            Landmark(0.0, 1.0, Landmark.Type.INTERCEPT, "left zero, x negative 3"),
            Landmark(1.0, 1.0, Landmark.Type.INTERCEPT, "right zero, x 3")
        )
        return LineChart(xMin, xMax, yMin, yMax, samples, landmarks)
    }
}
